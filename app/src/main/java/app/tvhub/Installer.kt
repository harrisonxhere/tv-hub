package app.tvhub

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface Job {
    data class Downloading(val done: Long, val total: Long) : Job {
        val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Checking : Job
    data object Installing : Job
    data class Failed(val message: String) : Job
    data class Held(val message: String) : Job
}

object Installer {
    /** 自更新用的任务 id；和 catalog 的 `owner-repo` 形式不会撞 */
    const val SELF_ID = "__self__"

    val jobs = mutableStateMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun busy(id: String) = jobs[id].let { it is Job.Downloading || it == Job.Checking || it == Job.Installing }

    /** 是否还有任务在跑（下载 / 校验 / 安装）。
     *  **刻意用独立的 `mutableStateOf`，而不是从 `jobs.values` 现算**：后者要依赖
     *  SnapshotStateMap 的集合读取被 Compose 追踪，实测存在「已经在下载、窗口却没拿到
     *  KEEP_SCREEN_ON」的漏触发；这里每一次任务变更都由 [put] 主动刷新，读取方拿到的
     *  就是一个普通 State<Boolean>，不会漏。
     *  `MainActivity` 用它给窗口挂 FLAG_KEEP_SCREEN_ON，避免电视在安装途中进屏保/休眠把下载掐断。 */
    var active by mutableStateOf(false)
        private set

    /** 所有对 [jobs] 的写入都走这里，顺带同步 [active]。 */
    private fun put(id: String, job: Job?) {
        if (job == null) jobs.remove(id) else jobs[id] = job
        active = jobs.values.any { it is Job.Downloading || it == Job.Checking || it == Job.Installing }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    fun start(ctx: Context, requested: App, force: Boolean = false) {
        /* 装哪个包以更新索引为准：catalog 里的 apks 是打包那天的，
           索引里才有最新 Release 的下载地址。没配置索引 / 没更新时原样返回。 */
        val app = UpdateIndex.effective(requested)
        val appCtx = ctx.applicationContext
        if (busy(app.id)) return
        if (!canInstall(appCtx)) {
            put(app.id, Job.Failed(str(R.string.err_allow_install)))
            openInstallPermission(ctx)
            return
        }
        val apk = pickApk(app)
        val file = File(Device.cacheDir(appCtx), "${app.id}-${app.version}-${apk.abi}.apk")
        put(app.id, Job.Downloading(0, apk.size))
        scope.launch {
            try {
                download(appCtx, apk, file) { done, total -> put(app.id, Job.Downloading(done, total)) }
                put(app.id, Job.Checking)
                /* force 只在用户看过「签名不同」的说明、主动选「仍然尝试」时传 true：
                   预检已经给过结论，再挡一次就变成死循环，交给系统去拒绝并回显原文。 */
                if (!force) {
                    val held = withContext(Dispatchers.IO) { checkSignature(appCtx, app, file) }
                    if (held != null) {
                        put(app.id, Job.Held(held))
                        return@launch
                    }
                }
                put(app.id, Job.Installing)
                try {
                    withContext(Dispatchers.IO) { commit(appCtx, app.pkg, app.id, file) }
                } catch (e: Exception) {
                    if (!handOff(appCtx, app.id, file)) throw e
                }
            } catch (e: Exception) {
                put(app.id, Job.Failed(e.message ?: str(R.string.err_generic)))
            }
        }
    }

    /**
     * 更新 OpenAPK TV 自己。和目录应用走同一条下载 + PackageInstaller 链路，
     * 但**不做** certSha256 比对：Android 本身就会拒绝签名不一致的覆盖安装，
     * 这里再挡一次，反而会在「将来换正式签名」时把用户卡在装不上的状态。
     */
    fun startSelfUpdate(ctx: Context, apk: Apk) {
        val appCtx = ctx.applicationContext
        if (busy(SELF_ID)) return
        if (!canInstall(appCtx)) {
            put(SELF_ID, Job.Failed(str(R.string.err_allow_install)))
            openInstallPermission(ctx)
            return
        }
        val file = File(Device.cacheDir(appCtx), "tvhub-${apk.name}")
        put(SELF_ID, Job.Downloading(0, apk.size))
        scope.launch {
            try {
                download(appCtx, apk, file) { d, t -> put(SELF_ID, Job.Downloading(d, t)) }
                put(SELF_ID, Job.Installing)
                try {
                    withContext(Dispatchers.IO) { commit(appCtx, appCtx.packageName, SELF_ID, file) }
                } catch (e: Exception) {
                    if (!handOff(appCtx, SELF_ID, file)) throw e
                }
            } catch (e: Exception) {
                put(SELF_ID, Job.Failed(e.message ?: str(R.string.err_generic)))
            }
        }
    }

    private suspend fun download(ctx: Context, apk: Apk, file: File, progress: (Long, Long) -> Unit) {
        if (file.exists() && file.length() == apk.size) return
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val part = File(file.path + ".part")
            var lastFailure: String? = null
            // Mirrors first, original GitHub URL last. A half-downloaded file must
            // never be handed to the next candidate (or to PackageInstaller).
            for (candidate in Mirror.candidates(apk.url)) {
                try {
                    fetchApk(ctx, candidate, part, apk.size, progress)
                    if (!part.renameTo(file)) throw IOException(str(R.string.err_save))
                    return@withContext
                } catch (e: Exception) {
                    lastFailure = e.message
                    part.delete()
                }
            }
            throw IOException(lastFailure ?: str(R.string.err_download_all))
        }
    }

    /** Downloads one candidate into [part]; throws on any failure so the loop can fall back. */
    private suspend fun fetchApk(
        ctx: Context, url: String, part: File, expectedSize: Long, progress: (Long, Long) -> Unit,
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "TVHub/${BuildConfig.VERSION_NAME} (Android TV)")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException(str(R.string.err_download_http, conn.responseCode))
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L
            var lastShown = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastShown > 256 * 1024) {
                            lastShown = done
                            val d = done
                            withContext(Dispatchers.Main) { progress(d, total) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun checkSignature(ctx: Context, app: App, file: File): String? {
        val fileCerts = Device.archiveCerts(ctx, file)
        if (fileCerts.isNotEmpty() && app.certSha256 != null && app.certSha256 !in fileCerts) {
            return str(R.string.held_key_changed, app.name)
        }
        val installed = Device.installed(ctx, app.pkg)
        if (installed != null && installed.certs.isNotEmpty() && fileCerts.isNotEmpty() && installed.certs.intersect(fileCerts).isEmpty()) {
            Catalog.sibling(app, installed)?.let { return str(R.string.held_sibling, app.name, it.repo) }
            return str(R.string.held_other_build, app.name)
        }
        return null
    }

    private fun commit(ctx: Context, pkg: String, id: String, file: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setSize(file.length())
        }
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).putExtra("appId", id).putExtra("apk", file.path)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(ctx, sessionId, intent, flags).intentSender)
        }
    }

    fun handOff(ctx: Context, id: String, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.apks", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(intent)
        put(id, null)
    }.isSuccess

    fun finished(id: String) {
        put(id, null)
        Device.tick++
    }

    fun failed(id: String, message: String) {
        put(id, Job.Failed(message))
        Device.tick++
    }

    fun cancelled(id: String) {
        put(id, null)
    }

    /** 用户看过「签名不同」的说明并关掉，把 Held 清掉，按钮回到正常文案 */
    fun clear(id: String) = put(id, null)

    /**
     * 唯一能真正装上的路径：先在系统卸载界面卸掉电视上那份别人签名的安装，卸完自动接着装。
     * 用 ACTION_DELETE 而不是 PackageInstaller.uninstall：后者在部分 TV ROM 上直接回失败且不给原因，
     * 前者是系统自带的卸载流程，兼容性最好。卸载完成靠 ACTION_PACKAGE_REMOVED 监听，不靠返回值。
     */
    fun uninstallThenInstall(ctx: Context, app: App) {
        val appCtx = ctx.applicationContext
        val pkg = app.pkg
        pendingRemoval?.let { runCatching { appCtx.unregisterReceiver(it) } }
        val watcher = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                if (intent.data?.schemeSpecificPart != pkg) return
                runCatching { appCtx.unregisterReceiver(this) }
                if (pendingRemoval === this) pendingRemoval = null
                start(appCtx, app)
            }
        }
        pendingRemoval = watcher
        runCatching {
            appCtx.registerReceiver(watcher, IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") })
            appCtx.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching { appCtx.unregisterReceiver(watcher) }
            pendingRemoval = null
            failed(app.id, it.message ?: str(R.string.err_generic))
        }
        clear(app.id)
    }

    /** 等卸载结果的那只一次性接收器，避免重复注册泄漏 */
    private var pendingRemoval: BroadcastReceiver? = null
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("appId") ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                val shown = confirm != null && runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!shown) {
                    val apk = intent.getStringExtra("apk")?.let(::File)
                    if (apk == null || !Installer.handOff(ctx, id, apk)) {
                        Installer.failed(id, str(R.string.err_blocked))
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.finished(id)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Installer.cancelled(id)
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                Installer.failed(id, str(R.string.err_conflict))
            PackageInstaller.STATUS_FAILURE_STORAGE -> Installer.failed(id, str(R.string.err_storage))
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Installer.failed(id, str(R.string.err_incompatible))
            else -> Installer.failed(id, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: str(R.string.err_generic))
        }
    }
}
