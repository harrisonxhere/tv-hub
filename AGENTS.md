# AGENTS.md — OpenAPK TV 项目说明书

> 给任何代码助手（Claude Code / Cursor / Codex / Copilot …）的第一份文件。
> 读完这一页就能动手改代码，不必翻历史聊天记录。
> 最后更新：2026-11-28 · 对应版本 1.0.4 (versionCode 15)

---

## 1. 这是什么

**OpenAPK TV** 是一个 Android TV 应用商店客户端：从 GitHub 抓好的目录里列出约 2,900 款开源 TV 应用，
从各项目 Releases 下载 APK 并调用系统 `PackageInstaller` 安装。

- 包名 / namespace：`app.tvhub`；显示名：`OpenAPK TV`
- 上游：[wingedpanther/openshelf](https://github.com/wingedpanther/openshelf) commit `1e18709`，**GPL-3.0-only**
- 本项目是**非官方衍生版**，首次修改 2026-11-14。相对上游的实质改动：
  1. 全量简体中文化（`values-zh-rCN`）
  2. 三档下载源（`Mirror.kt`）：自动 / 直连 GitHub / 国内镜像，简中系统默认走镜像
  3. 品牌更名换标（猫耳电视图标，`art/`）
  4. **界面按 v8 设计稿整体重做**（8 个视图，Kotlin 里手写排版，不是 Leanback 模板）

一句话定位：**不是通用应用市场，是「GitHub 开源 TV 应用」的策展式入口**，目录是静态快照，不做实时爬取。

---

## 2. 硬性约束（改之前必须知道）

1. **许可证** GPL-3.0-only，不能换成 or-later / MIT / Apache。
   `NOTICE.md`、`README.md` / `README.zh-CN.md` 顶部衍生声明里的**来源声明必须保留**（GPL 要求随分发给出完整声明与源码）；
   **应用界面内不点名上游**（用户 2026-11-25 要求），关于页只写「独立修改版 + 首次修改日期 + GPL-3.0 + 仓库地址」，
   这条底线也不能删 —— GPL-3.0 §5(a) 要求修改版携带「已修改」与日期的提示。
   **更新日志 / GitHub Release 说明也不写「衍生自 XXX」**（用户 2026-11-26 要求）：上游来源只在两份 README 顶部声明和
   `NOTICE.md` 各写一次，够了；发布说明的许可段只留「GPL-3.0-only + 源码/改动见 NOTICE.md」。
   更名只允许改「自称」，发 APK 必须同渠道给源码。
1b. **README 多语言**（用户 2026-11-26 要求）`README.md` 是**英文原文 / 默认**（GitHub 首屏就展示它），
   中文版在 `README.zh-CN.md`。两份顶部都有一行语言列表（当前语言加粗，其余是链接），改动任一份都要**同步另一份**
   的结构与版本号。新增语言：复制 `README.zh-CN.md` 为 `README.<code>.md`（如 `README.ja.md`），翻译，
   再到**所有** README 的语言行里挂上链接。日后的德语等语言都按这个约定加，不要再塞回同一个文件。
2. **多语言** 任何用户可见文案必须走 `str(R.string.xxx)`。
   新增键要**同时**加两份：`app/src/main/res/values/strings.xml`（英文基准）+ `values-zh-rCN/strings.xml`（中文）。
   代码里出现中文字面量就是 bug。数据（应用描述）来自上游 catalog，是英文，属已知数据层缺口，不要假装已解决。
3. **设计坐标** 界面按 **1280×720 设计稿**写死 dp，`DesignCanvas` 会用 `LocalDensity` 整体缩放
   （1080p 电视 960×540dp → 缩放 0.75）。位置一律用 `Modifier.at(x, y)`（= `offset(x.dp, y.dp)`），
   **不要**在视图内部再引入自适应布局；滚动容器只允许出现在两处：`RoomScreen` 的 `LazyVerticalGrid`
   和搜索结果的 `LazyColumn`，别处加滚动会破坏设计稿的整屏构图。
   画布只吃**安全区**（`WindowInsets.systemBars ∪ displayCutout`），墙纸 `Wall` 仍铺满整块物理屏幕：
   TV 的 insets 恒为 0（不受影响），手机贴高时顶栏/齿轮不再落进状态栏的触摸带。IME 不算进安全区，
   否则软键盘一弹整屏构图会跳。
4. **遥控器优先** 大屏 10-foot UI：只有焦点，没有鼠标、没有 hover、没有滚动条。
   所有可点元素必须包在 `Focusable {}` 里，焦点态用 `Modifier.ring(...)`。
5. **整图不裁切** 用户明确要求 banner / 封面「整张原图显示」。
   用 `ContentScale.Fit` + 左右羽化（`MaskedArt`），**不要**改成 `Crop` 或裁边。
6. **安装不可绕过签名校验** `Installer.kt` 里 `certSha256` 与上游 APK 字节校验逻辑不要动。
   校验不通过时状态是 `Job.Held`，**必须**由 UI 解释清楚（`JobDialog`）并给出可执行的下一步——
   只写状态不渲染，用户看到的就是「点了更新没反应」，1.0.3 之前就踩过这个坑。
   `Job.Failed` 同理，用同一个弹层回显系统原文。
7. **发布签名不能换** 1.0.1 及更早的包是 CI 用 runner 临时 debug 密钥签的（证书 `CN=Android Debug`，
   私钥随 runner 销毁，找不回来）；1.0.2 起改用固定密钥 `D:/Harrison/keystores/openapk-tv/openapk-tv.jks`
   （`CN=OpenAPK TV`，SHA-256 `b1c54698…`），CI 从 secrets `KEYSTORE_B64` 还原并用 apksigner 核对指纹。由此：
   ① 从 1.0.1 升级必须**先卸载**（直接装报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`），1.0.2 及之后互相覆盖正常；
   ② 这把 key 丢了就再也发不出能覆盖安装的包，等于让所有用户重装一次，所以本地要留备份、别删；
   ③ 不要用 debug key 发版，也不要为了省事换 key（换 key = 断更一次）。

---

## 3. 技术栈

| 项 | 版本 / 值 |
|---|---|
| 语言 | Kotlin 2.4.21（无 kapt / 无 ksp） |
| UI | Jetpack Compose + `androidx.tv:tv-material:1.1.0`（实际几乎全手写，只借用 Compose 基础件） |
| Compose BOM | `2026.09.00` |
| AGP / Gradle | 9.4.1 / 9.8.2（腾讯云镜像发行版） |
| SDK | compileSdk 37 · targetSdk 36 · **minSdk 28**（真机边界：小米电视 Android 9） |
| Java | 17 |
| 依赖仓库 | 阿里云镜像优先，`google()` / `mavenCentral()` 兜底（**不能只写国内源**，AGP 在部分镜像 404） |
| 第三方库 | 只有 `activity-compose`、`kotlinx-coroutines-android`。**刻意不引入 Retrofit / Coil / Room / Hilt**，网络用 `HttpURLConnection`，图片自己 decode 缓存 |

---

## 4. 目录结构

```
openapk-tv/
├── AGENTS.md                  ← 本文件（AI 入口）
├── CLAUDE.md                  ← 指针，内容同 AGENTS.md
├── README.md                  ← 项目介绍（**英文，默认**，GitHub 首屏；含衍生版声明，勿删）
├── README.zh-CN.md            ← 同一份介绍的中文版（顶部语言行互链）
├── NOTICE.md                  ← GPL 合规声明：来源 commit、修改日期、第三方资产
├── LICENSE                    ← GPL-3.0 原文，不动
├── FONT-LICENSE-OFL.txt       ← Geist 字体许可
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/catalog.json        ← 4.1MB 目录快照，3582 款应用 / 19 分类（快照 2026-11-22）
│       ├── java/app/tvhub/            ← 全部源码，7 个文件 5912 行
│       └── res/values{,-zh-rCN}/strings.xml
├── art/                       ← 品牌标识（设计真源）
│   ├── logo.svg               ← 应用图标/横幅的真源：猫耳电视 + 屏幕内机器人头（1024 画布）
│   ├── logo-mark.svg          ← 顶栏/启动页真源：白色电视轮廓 + 绿色渐变笑脸（620×542，无圆底）
│   ├── logo-badge.svg         ← 旧徽标（绿渐变圆底 + 白电视），已不在应用内使用，留档
│   ├── generate_icons.py      ← 由 logo-1024.png 生成 5 档启动器图标 + 5 档 TV 横幅
│   └── source/                ← logo-1024.png（logo.svg 的 Chrome 渲染产物）+ 原始 svg 备份
├── .github/workflows/updates.yml  ← 每日抓取应用更新 → data/updates/ + updates release
├── .github/workflows/release.yml  ← 推 v* tag 即构建并发版（附件 + sha256，正文取 docs/releases/）
├── data/catalog-source-2026-11-22.json  ← 上游目录原始快照（含 downloads/perms 等全字段，重新生成 catalog.json 用）
├── docs/
│   ├── ARCHITECTURE.md        ← 数据流、状态管理、安装链路
│   ├── DESIGN-SYSTEM.md       ← 设计坐标系、颜色令牌、焦点环、图片渲染
│   ├── SCREENS.md             ← 8 个视图逐个的坐标与结构
│   ├── BUILD-AND-TEST.md      ← 构建、模拟器、adb、截图、切语言、发版
│   ├── DECISIONS.md           ← 为什么长这样（v1→v9 决策与否定记录）
│   ├── INTRO.md               ← 对外介绍（可直接当项目页正文）
│   ├── releases/              ← 每个版本的发布说明，`<tag>.md` 由 CI 直接当 Release 正文
│   └── screenshots/current/   ← 当前界面实拍（3 张，浅/深主题 + 设置）
└── tools/
    ├── fetch_updates.py       ← 更新索引采集（REST + 分层调度）
    └── reclassify.py          ← 给 catalog 补 group / sub
```

---

## 5. 代码地图

| 文件 | 行数 | 职责 |
|---|---|---|
| `MainActivity.kt` | 73 | 唯一 Activity。初始化顺序固定：`Prefs.init` → `L.init` → `Images.init`（顺序错了语言/主题会失效） |
| `Data.kt` | 997 | `Catalog`（读 assets/catalog.json）、`App`/`Apk` 模型、`pickApk()` ABI 选择、`L`+`str()` 本地化、`Fmt` 格式化、`Images` 内存+磁盘图片缓存、`Device` 已安装列表、`Prefs` 偏好 |
| `Ui.kt` | 571 | 设计系统层：`DesignCanvas` 坐标系、`C` 颜色令牌、`Wall` 背景、`T` 字体、`Icons` 24×24 自绘图标、`Focusable`、`Modifier.ring`、`cardShadow`、`RoundBtn`、`GlassPill`、`Ring` |
| `Screens.kt` | 3658 | 根组合 `TvHub()` + `TopBar` + 8 个视图 + 全部子部件 + 图片渲染工具（`art` / `MaskedArt` / `edgeColors` / `vivid` / `wallTints`） |
| `Installer.kt` | 317 | 下载 → `PackageInstaller` 会话写入 → 结果 `BroadcastReceiver` → 状态机（`Job`）；`checkSignature()` 的两道预检把结果写成 `Job.Held`，详情页再用 `JobDialog` 解释原因并给「先卸载再装 / 仍然尝试」；`uninstallThenInstall()` 走系统卸载界面（`ACTION_DELETE`）+ 监听 `ACTION_PACKAGE_REMOVED` 续装；`startSelfUpdate()` 是自更新入口，跳过签名比对 |
| `Mirror.kt` | 67 | 下载源三档（自动 / 直连 / 镜像）+ 系统语言判定：把 `github.com` / `raw.githubusercontent.com` 重写成镜像候选（`gh-proxy.com` → `ghproxy.net` → 原链），自动档只在简中系统走镜像 |
| `UpdateIndex.kt` | 229 | 更新索引：拉 `updates.json.gz`（ETag/304、缓存 `filesDir/updates`）、按 tag 数字段判定「有新版本」、`effective(app)` 把最新版本与 APK 叠加到 catalog 条目上；`self` 段用于 OpenAPK TV 自更新 |

**视图清单**（都在 `Screens.kt`，全部 `private`，由 `TvHub()` 分发）

| 视图 | 组合函数 | 入口 |
|---|---|---|
| 发现（首页） | `HomeScreen` | `Tab.Discover` |
| 分类 | `CatsScreen` | `Tab.Categories`，点磁贴 → `RoomScreen` |
| 房间（单分类列表） | `RoomScreen` | `room != null` 时覆盖在分类之上 |
| 我的应用 | `MineScreen` | `Tab.Mine` |
| 搜索 | `SearchScreen` | 顶栏放大镜 / `Tab.Search` |
| 详情 | `DetailScreen` | `detail != null`（任何卡片按 OK） |
| 设置 | `SettingsScreen` | 顶栏齿轮 / `Tab.Settings` |
| 关于 | `AboutScreen` | 设置页「关于」→ `about != null`，覆盖在设置之上 |

状态全在 `TvHub()` 里用 `remember { mutableStateOf(...) }` 管：`tab`、`detail`、`room`、`about`、`chip`、`byDate`、`heroIdx`、`query`、`typing`、`toast`。**没有 ViewModel，没有导航库**——刻意保持单文件可读。

---

## 6. 构建 / 运行 / 验证

```bash
# 编译（约 1–2 分钟）
cd D:/Harrison/Code/openapk/openapk-tv
./gradlew :app:assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk

# 装到设备（debug 包带 testOnly，必须 -t）
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# 重启应用（改完必做，首次冷启动读 catalog.json 要 ~20s）
adb shell am force-stop app.tvhub && adb shell am start -n app.tvhub/.MainActivity
```

`local.properties` 里的 `sdk.dir` 是本机绝对路径，**不纳入版本库**（已在 `.gitignore`）；
换机器要重新指向 SDK。详细流程（模拟器 AVD、切语言、截图、常见 adb 坑）见 `docs/BUILD-AND-TEST.md`。

**改版本号**（发版才改，日常提交不动它）

```kotlin
// app/build.gradle.kts
versionCode = 11        // 每次发版 +1，与 versionName 无关
versionName = "1.0.1"   // semver，给人看的
```

`versionCode` **只增不减**：真机装过上游版本 0.6.0（vc10），比它小的包直接
`INSTALL_FAILED_VERSION_DOWNGRADE`。所以起点是 11，之后逐次加一。

不采用 `major*10000+minor*100+patch` 这类公式：起点已被上游的 10 占住，公式要么跳到一个大数
（1.0.0 → 10000）、要么在 0.x 阶段算出比 10 还小的值；单线发版「+1」最不容易算错。

`User-Agent` 取的是 `BuildConfig.VERSION_NAME`，不要再写死字符串到网络代码里。

**发版**

改版本号 → 写 `docs/releases/v<ver>.md` → 提交推 main → 打 `v<ver>` tag 推上去。
`.github/workflows/release.yml` 会校验 tag 与 `versionName` 一致、构建 release 包、发 Release
（附件 `openapk-tv-<ver>.apk` + `.sha256`，标题 `OpenAPK TV <ver>`，正文取那份 md）。

更新日志**一份文件双语**：英文在上、`---` 分隔、中文在下，各写一句概括 + 最多 5 条，安装说明只留最后一段。
骨架与规则见 `docs/releases/TEMPLATE.md`（用户要求：简短，别写成 changelog 论文）。

```bash
git tag -a v1.0.4 -m "v1.0.4"
git push origin main && git push origin v1.0.4   # 先 main 后 tag，别再 --follow-tags
```

**不要**再去网页手工建 Release：标题、正文、附件三样都容易漏（v1.0.1 就漏成了草稿）。
细节与签名注意事项见 `docs/BUILD-AND-TEST.md` §8。

---

## 7. 改代码的固定套路

**加一条界面文案**
1. `values/strings.xml` 加英文键
2. `values-zh-rCN/strings.xml` 加同键中文
3. 代码里 `str(R.string.xxx)`，带参数用 `%1$s` / `%1$d`
4. 不要复用同一个键表达两种语义（中英文语序不同，复用必翻车）

**调一个视图的布局**
1. 对照 `docs/SCREENS.md` 的坐标表直接改 Kotlin，改完截图确认（1280×720）
2. 像素值直接用 dp（`863.dp` 就是 863 像素），不要换算
3. 只改 `at(x, y)` / `width` / `height` / `spacing`，不要新增 wrapper
4. 深浅两个主题各截一张图验收

**加一个卡片 / 可点元素**
```kotlin
Focusable(onClick = onOpen, modifier = Modifier.ring(false, 20.dp), requester = r) { f ->
    Box(Modifier.ring(f, 20.dp, 1.015f)) { /* content */ }
}
```
`ring` 要在外面再套一层传 `focused`，`Focusable` 本身不画环。

**跨视图换焦点**（进详情 / 进房间 / 切 tab 之后）
用 `handoff.pending = true`，让 `TvHub()` 里的 `LaunchedEffect` 去重试 `requestFocus()`——
新视图控件此刻还没组合好，直接请求会抛 `IllegalStateException`。

---

## 8. 已知坑（都真实踩过，别再踩）

1. **`remember` 缓存空目录**：`hero`/`tints` 必须在 `if (Catalog.apps.isEmpty()) return` **之后**计算，且 key 带 `Catalog.apps.size`。否则首屏永远空白。
2. **`Prefs.init` 必须早于 `L.init`**。反了的话 `L` 不知道用哪种语言，重启后语言回落到系统语言。
3. **`str()` 里读 `L.rev` 是刻意的**——这是语言切换的重绘订阅机制，别当无用代码删掉。也**不能**在组合过程中 `rev++`，会无限循环。
4. **顶栏与内容区不能共用一个 `FocusRequester`**（`navReq` 与 `handoff.target` 是两套）。共用会导致进设置页后焦点卡在顶栏、按 OK 无反应。
5. **浅色主题白卡贴白底**：靠 `Modifier.cardShadow()` 分层，深色下它返回原 Modifier。删掉就一片糊。
6. **白底 banner 取色会得到炭黑**：`vivid()` 会按色相提纯 + 钳位饱和度/明度（`FALLBACK_HUES = 6f/174f/226f` 红/青/蓝）。不要直接用 `meanColor`。
7. **分类磁贴背景字标必须模糊**：做法是取 `art(url, 32)` 的 32px 缩略图再 `Crop` 放大，天然糊。改成清晰图会和前景房间名打架。
8. **详情页横幅高度**：左列 `fillMaxHeight()`，横幅 `weight(1f)`，`590dp = 720 - 30(下边距) - 70(起始偏移)`。改这两处数字要同步，否则底部留死白或溢出。
9. **`Fmt.gbNum()` 返回纯数字**，单位在字符串资源里拼。之前用 `gb().substringBefore(" GB")` 依赖英文字面量，切中文就崩。
10. **热词 / 时段是 `<string-array>`**（`hot_words`、`recent_times`），不是 Kotlin 常量。
11. **品牌标识有两份，别搞混**：顶栏/启动页走 `Ui.kt` 的 `logoMark(shell, screen)`（真源 `art/logo-mark.svg`，白电视壳 + 绿渐变笑脸，620×542），是**彩色**向量，必须用 `Brand(dp)` 渲染 —— 用 `Ico()` 上 tint 会把壳和脸染成同一个颜色，直接看不见。另外**颜色随主题**，是两层：深色白壳、屏幕留空；浅色壳 `#363B45` + 屏幕填 `#0B1017`。浅色只换壳色不够 —— 屏幕透出墙色时绿脸浮在浅灰上，整枚像线框图标，压不住 17sp 粗体字标（实拍对比过）。屏幕矩形必须与壳的镂空同形（`LOGO_SCREEN`），差一单位就露白边。启动器图标与 TV 横幅走另一条链：`art/logo.svg` → Chrome 渲染 `art/source/logo-1024.png` → `python art/generate_icons.py`（仍是带绿圆底的旧徽标）。改应用内标识只动前者，改桌面图标才动后者。
    **手机/Android TV 启动器实际用的是自适应图标**（`mipmap-anydpi-v26/ic_launcher(.round).xml`，背景 `#FFFFFF`）：前景图由官网 `openapk-web/public/logo.svg` 经 Chrome 渲染成 `art/source/logo-web.png`，再跑 `python art/generate_adaptive_icon.py` 出 5 档前景 + 单色剪影。只想调图形大小就改脚本里的 `WIDTH_DP`（当前 48dp，可见区 72dp 的 67%）。
12. **应用首次冷启动要 ~20s** 读 4.1MB catalog（模拟器 x86 无加速更慢）。截图前 sleep 不够会拍到 Splash。
13. **不要改 `applicationId`**：`app.tvhub` 与上游 `io.github.wingedpanther.openshelf` 已是不同应用，可并存。再改会让用户已装版本变成孤儿。
14. **自更新看的是 `versionName`，不是 `versionCode`**：索引里的 `self.tag` 只带版本号，`selfNewer()` 比数字段。发版时**两个都要改** —— 只 bump `versionCode` 的话，用户永远看不到「发现新版本」。
15. **`self` 段不跟着应用调度走**：`fetch_updates.py` 在「无到期应用」时会单独走 `refresh_self()`。如果把它塞回主流程的 `if not picked: return` 后面，自更新就要等某个应用到期才生效（踩过）。
16. **改了 App/AppArt/Apk/Release 字段必须 BinCache.FORMAT +1**：二进制缓存格式升级时，必须把 `BinCache.FORMAT` 常量加 1，否则已缓存的旧格式文件会被误读，导致数据错乱或崩溃。
17. **判断系统语言不能用 `Locale.getDefault()`**：`L.apply()` 会把它改成 App 内语言；而且系统语言变化时
    Activity 重建、进程通常还活着，读到的仍是上一次的值。要读 `Resources.getSystem().configuration.locales[0]`。
18. **`DesignCanvas` 的底色要跟主题**：它是启动页唯一的背景（启动分支没有 `Wall()`），原来写死 `#06080B`，浅色主题下会先黑一下再变亮，深灰壳的 logo 贴在近黑底上也等于没有轮廓。现在 `if (C.dark) #06080B else C.Wall`。
19. **Dialog 不要盖在启动页上**：主窗口还没拿到焦点时再叠一个 Dialog 窗口，输入事件无处可去，
    实测直接 ANR（`Input dispatching timed out, no focused window`）。`MirrorAskDialog` 必须等
    `Catalog.apps` 非空之后再弹；这也是它排在 `if (Catalog.apps.isEmpty()) return` 之后的原因。
20. **`updates.json` 是全量快照，不是增量**：`fetch_updates.py` 每轮只查「到期 / 热层」的一部分应用，
    如果只写本轮结果，没轮到的应用就会从索引里消失，客户端与网页一起退回 catalog 快照里的旧版本
    （实测 SmartTube 32.80s 被 32.71s 覆盖）。所以「3.5 沿用上一份索引」这一步不能删：
    本轮没查到、且上一份里的 tag 仍比 catalog 版本新的条目，原样带过来。`--sample` 抽样自检时跳过这步。

---

## 9. 当前状态

**已完成**
- 品牌更名 + 猫耳电视图标（5 档 launcher + 5 档 banner，符合 TV-BN 含应用名）
- 全量中文化（300+ 条，含日期格式 `yyyy年M月d日`）
- 下载源三档（自动 / 直连 GitHub / 国内镜像）+ 三级回退；简中系统默认走镜像，语言发生变化时弹窗询问一次
- v8 设计落地：7 个视图全部重写，深浅双主题
- 设置页「界面语言」切换（跟随系统 / 简体中文 / English），无需重启 Activity
- 详情页：卡片按 OK 进详情，横幅移至文字下方并自适应撑满剩余高度
- 正式发布密钥已生成（`D:/Harrison/keystores/openapk-tv/`，指纹 `b1c54698…b11d7183`），
  本地与 CI 共用；CI 用 Secrets `KEYSTORE_B64` / `STORE_PASSWORD` / `KEY_PASSWORD`
- 发版走 CI：推 `v*` tag 自动构建 + 发 Release，日志在 `docs/releases/<tag>.md`（双语）

**未做 / 下一轮候选**
- 3,582 条应用描述仍是英文，中文关键词搜索命中率低 → 需要独立的中文描述层，**不要改上游 catalog.json**
- 「下载队列」「收藏」仍是 toast 演示；筛选面板未实现
- v1.0.0 已下架（旧品牌名）；已装 v1.0.0 / v1.0.1（debug 签名）的机器要装新密钥签的包，
  **必须先卸载** —— 之后再不用卸载
- `docs/screenshots/` 里上游旧截图品牌已过时

---

## 10. 文档索引

| 想了解 | 看 |
|---|---|
| 数据怎么流、安装怎么走 | `docs/ARCHITECTURE.md` |
| 颜色 / 字号 / 焦点环 / 羽化算法 | `docs/DESIGN-SYSTEM.md` |
| 某个视图的坐标与结构 | `docs/SCREENS.md` |
| 怎么编译、怎么在电视上调试 | `docs/BUILD-AND-TEST.md` |
| 为什么是现在这个设计（含被否定的方案） | `docs/DECISIONS.md` |
| 对外介绍 / 项目页正文 | `docs/INTRO.md` |
| 项目页正文（多语言，英文为默认） | `README.md`（English，GitHub 首屏）· `README.zh-CN.md`（中文） |
| 视觉真源 | `app/src/main/java/app/tvhub/Ui.kt` |
