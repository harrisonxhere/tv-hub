package app.tvhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

enum class Tab(val label: Int) {
    Discover(R.string.tab_discover),
    Categories(R.string.tab_categories),
    Search(R.string.tab_search),
    Mine(R.string.tab_mine),
    Settings(R.string.tab_settings),
}

class FocusHandoff {
    val target = FocusRequester()
    var pending by mutableStateOf(true)
}

/** 分类色点：与原型 CHIPS 配色一致（详情页标签、分类页共用） */
private fun chipColor(k: String) = when (k) {
    "Streaming" -> Color(0xFFC86BA0)
    "Video" -> Color(0xFFE0795C)
    "Video players" -> Color(0xFF4FA3A0)
    "Launchers" -> Color(0xFF5B8FC9)
    "Tools" -> Color(0xFFA9906B)
    "VPN & network" -> Color(0xFF6D8BC7)
    "Games" -> Color(0xFF8772C4)
    else -> Color(0xFF8A8F99)
}

/** 一级分类磁贴的小图标：键是 group（12 个 ASCII key），不是上游 room */
private fun groupIcon(k: String) = when (k) {
    "video" -> Icons.Play
    "photos" -> Icons.Tv
    "music" -> Icons.Music
    "games" -> Icons.Pad
    "reading" -> Icons.Book
    "social" -> Icons.Chip
    "browser" -> Icons.Search
    "home" -> Icons.Home
    "desktop" -> Icons.Grid
    "stores" -> Icons.Down
    "ai" -> Icons.Star
    "system" -> Icons.Tool
    "other" -> Icons.Dots
    else -> Icons.Grid
}

private fun appsOfGroup(k: String): List<App> =
    Catalog.visible.filter { it.group == k }.sortedByDescending { it.stars }

private fun groupTotal(k: String): Int = Catalog.apps.count { it.group == k }

/** 上游原始标签维度的取数：详情页「同房间」用它，粒度比一级分类细 */
private fun appsOfRoom(k: String): List<App> =
    Catalog.visible.filter { it.room == k }.sortedByDescending { it.stars }

private fun roomTotal(k: String): Int = Catalog.apps.count { it.room == k }

/* ============================================================
   根
   ============================================================ */
@Composable
fun TvHub() {
    val activity = LocalContext.current as Activity
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Catalog.load(ctx)
        /* 目录一读完就开始在后台把首屏的图拉到磁盘上 */
        Warm.start()
    }

    var tab by remember { mutableStateOf(Tab.Discover) }
    var detail by remember { mutableStateOf<String?>(null) }
    var room by remember { mutableStateOf<String?>(null) }
    /* 关于页是叠在设置之上的二级页：只从设置进入，返回时回到设置
       （tab 不动，所以不需要像 room 那样记来源） */
    var about by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var homeMemo by remember { mutableStateOf<String?>(null) }
    /* 上次在首页聚焦的元素：memo 是应用 id（null = 主推位），row 是它在货架里的行号。
       返回首页时先滚回这一行再抢焦点，否则主推卡已被 LazyColumn 回收，会落到无焦点状态 */
    var homeRow by remember { mutableIntStateOf(0) }
    var roomMemo by remember { mutableStateOf<String?>(null) }
    var lastInstalled by remember { mutableStateOf<String?>(null) }
    var settingsFrom by remember { mutableStateOf(Tab.Discover) }
    val handoff = remember { FocusHandoff() }
    val heroReq = remember { FocusRequester() }
    val navReq = remember { Tab.entries.associateWith { FocusRequester() } }
    /* 首页货架是可滚动的：状态提到这里，进详情再返回时滚动位置不丢 */
    val homeList = rememberLazyListState()

    if (Catalog.apps.isEmpty()) {
        DesignCanvas { Splash(Modifier.align(Alignment.Center)) }
        return
    }

    /* 系统语言刚切成简体中文（见 Prefs.noteSystemLanguage）：问一次要不要把下载源换成国内镜像。
       必须等目录就绪之后再弹 —— 启动页阶段主窗口还没有焦点，上面再盖一个 Dialog 窗口会让
       输入事件无处可去（实测直接 ANR：Input dispatching timed out, no focused window）。 */
    if (Prefs.askMirror) MirrorAskDialog(
        onSwitch = {
            Prefs.useSource(Prefs.SOURCE_MIRROR)
            toast = str(R.string.toast_source, str(R.string.source_mirror))
        },
        onKeep = { Prefs.askMirror = false },
    )

    // 必须等目录加载完再算：否则 remember 会把「空列表」缓存下来，首屏永远是空的
    // Images.epoch 也进 key：某张横幅被判定取不到时要能把这一项从主推位换掉
    val hero = remember(Catalog.apps.size, Prefs.hideStreams, Images.epoch) { Hero.playlist() }
    // 只跟数量绑定：坏图被替换时列表内容会变，但游标不该跟着跳回起点
    var heroIdx by remember(hero.size) { mutableIntStateOf(Hero.start(hero.size)) }
    var heroFocus by remember { mutableStateOf(false) }
    val heroTints = wallTints(hero)

    val notFound = detail?.let { Catalog.byId(it) }

    // 详情页背景跟着这个应用走：拿应用图标的主色，撑起同样三团色雾
    val detailWall = art(notFound?.art?.icon ?: notFound?.art?.wall, 24)
    val appTints = remember(detailWall) {
        detailWall?.let { b -> val c = vivid(meanColor(b), 6f); listOf(c, c, c) } ?: emptyList()
    }
    val tints = if (appTints.isNotEmpty()) appTints else heroTints

    fun openDetail(id: String) { detail = id; handoff.pending = true }
    fun closeDetail() { detail = null; handoff.pending = true }
    fun openRoom(k: String) { room = k; handoff.pending = true }
    fun closeRoom() { room = null; handoff.pending = true }
    fun closeAbout() { about = false; handoff.pending = true }
    /* 子页没有导航栏，返回一律回首页 */
    fun goHome() { tab = Tab.Discover; typing = false; handoff.pending = true }

    BackHandler {
        when {
            about -> closeAbout()
            detail != null -> closeDetail()
            room != null -> closeRoom()
            tab == Tab.Search && query.isNotEmpty() -> { query = ""; handoff.pending = true }
            tab != Tab.Discover -> { tab = Tab.Discover; handoff.pending = true }
            else -> activity.finish()
        }
    }

    LaunchedEffect(toast) { if (toast != null) { delay(1900); toast = null } }

    /* 主推位轮播：只在首页、无子页、且焦点不在大卡上时才走表。
       焦点在大卡上必须暂停，否则用户盯着 A 按 OK 会打开已经切过去的 B */
    LaunchedEffect(tab, detail, room, heroIdx, heroFocus, hero.size) {
        if (tab != Tab.Discover || detail != null || room != null || heroFocus || hero.size < 2) {
            return@LaunchedEffect
        }
        delay(HERO_INTERVAL_MS)
        val next = (heroIdx + 1) % hero.size
        heroIdx = next
        Prefs.useHero(next)
    }

    /* 提前把下一张解好，切换那一帧不要空白。maxWidth 必须和 HeroBig 里的一致，否则不命中缓存 */
    LaunchedEffect(hero, heroIdx) {
        if (hero.size < 2) return@LaunchedEffect
        hero[Math.floorMod(heroIdx + 1, hero.size)].art.banner?.let { Images.load(it, HERO_WIDTH) }
    }

    DesignCanvas(tints) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 30.dp)) {
                Panel(
                    Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                        if (tab != Tab.Search || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val ch = e.utf16CodePoint
                        when {
                            e.key == Key.Backspace -> { query = query.dropLast(1); typing = true; true }
                            ch in 32..126 -> { query += ch.toChar(); typing = true; true }
                            else -> false
                        }
                    },
                ) {
                    /* 一级页（发现 / 分类 / 我的应用）都有顶部导航；
                       二级页（房间 / 详情 / 搜索 / 设置 / 关于）无导航、左上返回 */
                    val subPage = detail != null || room != null || about ||
                        tab == Tab.Search || tab == Tab.Settings
                    if (!subPage) TopBar(
                        tab = tab,
                        deep = room != null,
                        onTab = { t ->
                            if (t == Tab.Search) { typing = false }
                            tab = t
                            handoff.pending = true
                            if (t != Tab.Mine) lastInstalled = lastInstalled
                        },
                        requesters = navReq,
                        onSearch = { tab = Tab.Search; typing = false; handoff.pending = true },
                        onGear = { settingsFrom = tab; tab = Tab.Settings; handoff.pending = true },
                        handoff = handoff,
                    )
                    val app = notFound
                    val r = room
                    when {
                        /* 关于页盖住设置页（tab 仍是 Settings），所以排在最前 */
                        about -> AboutScreen(
                            toast = { toast = it },
                            onBack = { closeAbout() },
                            handoff = handoff,
                        )
                        app != null -> DetailScreen(
                            app = app,
                            onBack = { closeDetail() },
                            onOpen = { openDetail(it) },
                            handoff = handoff,
                        )
                        r != null -> RoomScreen(
                            room = r,
                            last = roomMemo,
                            onFocusApp = { roomMemo = it },
                            onBack = { closeRoom() },
                            openDetail = { openDetail(it) },
                            handoff = handoff,
                        )
                        else -> when (tab) {
                            Tab.Discover -> HomeScreen(
                                hero = hero,
                                heroIdx = heroIdx,
                                heroRequester = heroReq,
                                onHeroFocus = { f ->
                                    heroFocus = f
                                    /* 主推位获得焦点 = 用户回到了最上面，返回时该落到这里 */
                                    if (f) { homeMemo = null; homeRow = 0 }
                                },
                                lastId = homeMemo,
                                onCardFocus = { id, row -> homeMemo = id; homeRow = row },
                                openDetail = { openDetail(it) },
                                openRoom = { openRoom(it) },
                                listState = homeList,
                                handoff = handoff,
                            )
                            Tab.Categories -> CatsScreen(
                                openRoom = { openRoom(it) },
                                handoff = handoff,
                            )
                            Tab.Mine -> MineScreen(
                                last = lastInstalled,
                                onFocusApp = { lastInstalled = it },
                                openDetail = { openDetail(it) },
                                openSettings = { settingsFrom = Tab.Mine; tab = Tab.Settings; handoff.pending = true },
                                toast = { toast = it },
                                handoff = handoff,
                            )
                            Tab.Search -> SearchScreen(
                                query = query,
                                onQuery = { query = it },
                                typing = typing,
                                onTyping = { typing = it },
                                openDetail = { openDetail(it); typing = false },
                                onBack = { goHome() },
                                handoff = handoff,
                            )
                            Tab.Settings -> SettingsScreen(
                                toast = { toast = it },
                                onAbout = { about = true; handoff.pending = true },
                                onBack = { tab = settingsFrom; handoff.pending = true },
                                handoff = handoff,
                            )
                        }
                    }
                    ToastHost(toast)
                }
            }
        }
    }

    // 新视图的目标控件可能还没组合出来：等两拍再抢焦点（首帧的自动聚焦会先落在左上）
    LaunchedEffect(handoff.pending, tab, room, detail, about) {
        if (!handoff.pending) return@LaunchedEffect
        /* 回首页先把货架滚回上次那一行：主推卡可能早已被 LazyColumn 回收，
           直接对 hero 抢焦点会失败，最后整页没有焦点 */
        val home = tab == Tab.Discover && detail == null && room == null && !about
        if (home) runCatching { homeList.scrollToItem(homeRow) }
        delay(120)
        val req = if (home && homeMemo == null) heroReq else handoff.target
        runCatching { req.requestFocus() }
        delay(80)
        runCatching { req.requestFocus() }
        handoff.pending = false
    }
}

@Composable
private fun Splash(modifier: Modifier = Modifier) {
    /* 启动页只有一两秒，品牌块要够重才看得见：96dp 徽标 + 38sp 名字
       （顶栏是 30dp / 17sp，两处本来就不是一套尺寸，别拉到一起） */
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Brand(96.dp)
            Spacer(Modifier.width(18.dp))
            T(str(R.string.app_name), 38.sp, weight = FontWeight.Bold, tight = true)
        }
        Spacer(Modifier.height(28.dp))
        /* 进度条：按已读字节估算，低端电视冷启动时让用户知道没卡死 */
        Box(Modifier.width(340.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.Fg.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(Catalog.progress).background(C.Fg.copy(alpha = 0.7f)))
        }
    }
}

/* ============================================================
   顶栏：品牌 / 胶囊导航 / 时钟 + 设置
   ============================================================ */
@Composable
private fun BoxScope.TopBar(
    tab: Tab,
    deep: Boolean,
    onTab: (Tab) -> Unit,
    requesters: Map<Tab, FocusRequester>,
    onSearch: () -> Unit,
    onGear: () -> Unit,
    handoff: FocusHandoff,
) {
    Box(Modifier.at(0, 4).width(1152.dp).height(48.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            /* 品牌：线条描边电视图标 + 名称 */
            Brand(36.dp)
            Spacer(Modifier.width(10.dp))
            T(str(R.string.app_name), 17.sp, weight = FontWeight.Bold, tight = true)

            Spacer(Modifier.width(36.dp))

            /* 导航胶囊：发现 / 分类 / 我的应用 —— 三项等分。
               等分宽度不能靠 weight：胶囊本身是 wrap-content，weight 会把剩下的大半条顶栏分给它。
               所以先量出三个标签里最宽的一个（SemiBold 是三项里最粗的），
               再给三项同一个 width，胶囊才会在胶囊里居中、左右对称。
               量出来的宽度跟着语言走，换到长标签的语言（My Apps / Meine Apps）也不会被裁。 */
            val entries = listOf(Tab.Discover, Tab.Categories, Tab.Mine)
            val labels = entries.map { t -> if (t == Tab.Mine) str(R.string.mine_title) else str(t.label) }
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val itemW = remember(labels.joinToString("\u0001"), density) {
                val widest = labels.maxOf { s ->
                    measurer.measure(
                        AnnotatedString(s),
                        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    ).size.width
                }
                with(density) { widest.toDp() } + 36.dp   // 左右各 18dp，与原来按文字撑开时的内距一致
            }
            Row(
                Modifier.height(44.dp).clip(CircleShape)
                    .background(if (C.dark) Color(0x75090C12) else Color(0x0E101620))
                    .border(1.dp, C.Line, CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEachIndexed { i, t ->
                    NavItem(
                        text = labels[i],
                        width = itemW,
                        selected = tab == t && !deep,
                        enabled = !deep,
                        onClick = { onTab(t) },
                        requester = requesters[t],
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            /* 搜索：胶囊外的独立方形按钮 */
            val onSearchTab = tab == Tab.Search && !deep
            TopIconBtn(
                icon = Icons.Search,
                onClick = onSearch,
                enabled = !deep,
                selected = onSearchTab,
            )

            Spacer(Modifier.weight(1f))

            /* 设置：最右 */
            if (!deep) TopIconBtn(Icons.Gear, onGear, enabled = true)
        }
    }
}

/** 顶栏方形图标按钮（40×40、12dp 圆角，对标原型 .ico-btn） */
@Composable
private fun TopIconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Focusable(onClick, Modifier.focusProperties { canFocus = enabled }) { f ->
        Box(
            Modifier.size(40.dp).ring(f, 12.dp, 1.06f)
                .background(
                    when {
                        f -> C.HiBg
                        selected -> C.Fg.copy(alpha = 0.14f)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, if (f) Color.Transparent else Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Ico(icon, 18.dp, if (f) C.HiFg else if (selected) C.Fg else C.Fg2) }
    }
}

@Composable
private fun NavItem(
    text: String,
    width: Dp,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    requester: FocusRequester? = null,
) {
    Focusable(onClick, Modifier.focusProperties { canFocus = enabled }, requester) { f ->
        /* 分段控件的外框只有 44dp 高、内边距 4dp：ring() 会向外扩 2(gap)+2(线)+7(光晕)dp，
           加上 1.06 放大，必然被父级 clip(CircleShape) 削平，还会盖到相邻胶囊上——
           截图里那道被切掉的白弧和「分类」左缘的月牙就是这么来的。
           所以导航项不走外扩焦点环：聚焦 = 反色实心白胶囊 + 深色字，边缘完整留在 gutter 内。 */
        val bg by animateColorAsState(
            when {
                f -> C.HiBg
                /* 选中态：Fg 半透明实心，不带描边。
                   对比度按「比静息高一个台阶、又明显低于聚焦反色」来定，
                   原来 18% 在深色主题里几乎看不出来，抬到 30%。 */
                selected -> C.Fg.copy(alpha = if (C.dark) 0.30f else 0.22f)
                else -> Color.Transparent
            },
            tween(140),
            label = "nav-item-bg",
        )
        Box(
            Modifier.width(width).height(36.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            T(
                text, 13.sp,
                color = if (f) C.HiFg else if (selected) C.Fg else C.Fg3,
                weight = if (f || selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

/* ============================================================
   首页：主推卡 + 本周热门 + 最近更新 + 12 个一级分类货架
   ============================================================ */
/* ---------------- 首页：可纵向滚动的分栏货架 ----------------
   顶部导航固定，滚动视口 72..660（588 高）。视口内节奏：
     主推 344 ─24→ 最近更新（标题 24 + 16 + 卡行 128）
              ─24→ 每个分类一栏（同构：标题 24 + 16 + 卡行 128）
   每栏 5 张卡：首位一张窄的「全部」卡（宽 128，卡面写这一栏的总数），
   后面 4 个应用等宽，横向间距 20 —— 应用卡宽 = (1152-128-80)/4 = 236。
   分栏集合与顺序 = 分类页的一级分类（GROUP_KEYS，后接 OTHER），
   名字走 groupName()、数量走 tile_count，和磁贴是同一套口径。 */
@Composable
private fun BoxScope.HomeScreen(
    hero: List<App>,
    heroIdx: Int,
    heroRequester: FocusRequester,
    onHeroFocus: (Boolean) -> Unit,
    lastId: String?,
    onCardFocus: (String, Int) -> Unit,
    openDetail: (String) -> Unit,
    openRoom: (String) -> Unit,
    listState: LazyListState,
    handoff: FocusHandoff,
) {
    if (hero.isEmpty()) return

    val pool = remember(Prefs.hideStreams) { Catalog.visible.toList() }
    val hot = remember(hero, Prefs.hideStreams) {
        pool.filter { a -> hero.none { it.id == a.id } }.sortedByDescending { it.stars }.take(4)
    }
    val recent = remember(Prefs.hideStreams) {
        pool.sortedByDescending { it.date }.take(5)
    }
    /* 分类分栏：和「分类」页同一套一级分类 —— 分组键是 group 而不是上游 room，
       顺序也照抄 GROUP_KEYS（「其它」压最后），不按数量重排。
       这样首页看到的 12 栏、名字、先后，与分类页的 12 个磁贴一一对应。
       每栏星标前 4，附带该分类的可见总数（副标用）。 */
    val shelves = remember(Prefs.hideStreams) {
        val byGroup = pool.groupBy { it.group }
        (GROUP_KEYS + GROUP_OTHER).mapNotNull { g ->
            val all = byGroup[g] ?: return@mapNotNull null
            Triple(g, all.sortedByDescending { it.stars }.take(4), all.size)
        }
    }

    /* 焦点换行时把整行对齐到视口顶部。LazyColumn 自带的 bringIntoView 只保证
       被聚焦的那张卡可见，行标题会被切掉；这里显式对齐，保证「标题 + 卡行」一起出现。
       行序号：0 主推、1 最近更新、2+i 第 i 个分类栏。 */
    val scope = rememberCoroutineScope()
    fun alignRow(index: Int) {
        scope.launch { runCatching { listState.animateScrollToItem(index) } }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.at(0, 72).width(1152.dp).height(588.dp),
        /* 顶部留 12：聚焦环的 halo 画在卡片 bounds 之外（gap 2 + halo 7），
           而 LazyColumn 会沿滚动方向裁掉越界内容，不留缝的话主推卡上边会被切一道 */
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        /* 主推卡 648 + 右侧本周热门 484，顶部对齐。
           热门标题 24 高、内容 38 起（比左侧少 2 是因为热门行之间有 6dp 行距，
           末行 272..344 才能与主推卡底边齐平）。 */
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().height(344.dp)) {
                HeroCarousel(
                    hero = hero,
                    idx = heroIdx,
                    modifier = Modifier.width(648.dp).height(344.dp),
                    requester = heroRequester,
                    onFocusChange = onHeroFocus,
                ) { openDetail(it) }
                Box(Modifier.offset(x = 668.dp).width(484.dp).height(344.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(24.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        T(str(R.string.sec_hot), 18.sp, weight = FontWeight.Bold, tight = true)
                        Spacer(Modifier.width(11.dp))
                        T(
                            str(R.string.sec_sub_short, Fmt.count(Catalog.apps.size)),
                            11.5.sp, Modifier.padding(bottom = 2.dp), color = C.Fg3,
                        )
                    }
                    hot.forEachIndexed { i, a ->
                        HotRow(
                            a = a,
                            modifier = Modifier.offset(x = 0.dp, y = (38 + i * 78).dp)
                                .fillMaxWidth().height(72.dp),
                        ) { openDetail(a.id) }
                    }
                }
            }
        }

        item(key = "recent") {
            Column {
                SectionHead(str(R.string.row_recent), str(R.string.row_recent_sub))
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().height(148.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    recent.forEach { a ->
                        WideCard(
                            a = a,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            requester = if (a.id == lastId) handoff.target else null,
                            onFocus = { onCardFocus(a.id, 1); alignRow(1) },
                        ) { openDetail(a.id) }
                    }
                }
            }
        }

        itemsIndexed(shelves, key = { _, s -> "shelf:" + s.first }) { i, shelf ->
            ShelfRow(
                group = shelf.first,
                apps = shelf.second,
                total = shelf.third,
                lastId = lastId,
                target = handoff.target,
                alignSelf = { alignRow(2 + i) },
                onCardFocus = { id -> onCardFocus(id, 2 + i) },
                openDetail = openDetail,
                openRoom = openRoom,
            )
        }
    }

    /* 视口底部渐隐：内容在 660 处被裁，硬切像被刀切，用 24dp 渐变收口，
       同时给「下面还有内容」一个视觉暗示。颜色取 Wall 的基色，不带色雾 */
    Box(
        Modifier.at(0, 636).width(1152.dp).height(24.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, C.Wall),
                ),
            ),
    )
}

/** 区块标题：18sp 粗体主标 + 11.5sp 灰副标，两者底对齐（首页所有分栏共用一套） */
@Composable
private fun SectionHead(title: String, sub: String) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.Bottom) {
        T(title, 18.sp, weight = FontWeight.Bold, tight = true)
        Spacer(Modifier.width(11.dp))
        T(sub, 11.5.sp, Modifier.padding(bottom = 2.dp), color = C.Fg3)
    }
}

/** 一个一级分类的货架：首位「全部」卡（带总数）+ 4 个应用。
 *  分组键是 group（和分类页磁贴同一个 key），不是上游 room。
 *  「全部」放最前面而不是末位：遥控器从左往右走，先经过入口再到精选，
 *  焦点一进这一行就能看到「这一栏一共有多少款」。 */
@Composable
private fun ShelfRow(
    group: String,
    apps: List<App>,
    total: Int,
    lastId: String?,
    target: FocusRequester,
    alignSelf: () -> Unit,
    onCardFocus: (String) -> Unit,
    openDetail: (String) -> Unit,
    openRoom: (String) -> Unit,
) {
    Column {
        /* 标题副标只留「精选了几款」——总数已经写在首位卡的卡面上，这里再报一遍是重复 */
        SectionHead(groupName(group), str(R.string.shelf_sub, apps.size))
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ViewAllCard(
                accent = groupAccent(group),
                total = total,
                modifier = Modifier.width(128.dp).fillMaxHeight(),
                requester = if (lastId == "all:$group") target else null,
                onFocus = { onCardFocus("all:$group"); alignSelf() },
            ) { openRoom(group) }
            apps.forEach { a ->
                WideCard(
                    a = a,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    requester = if (a.id == lastId) target else null,
                    onFocus = { onCardFocus(a.id); alignSelf() },
                ) { openDetail(a.id) }
            }
            /* 应用不足 4 个时先占空位，保证应用卡宽度不随数量变化 */
            repeat((4 - apps.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 分栏首位的「全部」卡：小字标签 + 大号总数，点进去就是这一分类的房间页。
 *  比应用卡窄一截（128 vs 236）是刻意的：它在节奏上属于「入口」而不是「内容」，
 *  宽度差一眼就能把两者分开，又仍然站在同一行里、跟着本栏主色走。
 *  不铺图，只用本栏主色的低饱和渐变打底；焦点态整张反色，与 WideCard 一致。 */
@Composable
private fun ViewAllCard(
    accent: Color,
    total: Int,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = tileShape(122.dp)
    /* 底在深色主题用深灰蓝、浅色主题用近白：渐变从「本栏主色」过渡到同一主色的低混比，
       卡片边缘始终有实体、不会在右下角化进背景。前景色跟着底走，两种主题都保住对比度。 */
    val base = if (C.dark) Color(0xFF1A1F28) else Color(0xFFF5F7FB)
    val ink = if (C.dark) Color.White else C.Fg
    Focusable(onClick, modifier, requester = requester, onFocus = onFocus) { f ->
        Column(Modifier.fillMaxSize()) {
            /* 不画外包边：边界交给「本栏主色渐变 + 投影」。1dp 的 C.Line 在一整排卡上会连成一条发灰的框 */
            Box(
                Modifier.fillMaxWidth().weight(1f).ring(f, tileRadius(122.dp), 1.015f)
                    .cardShadow(shape, 16.dp)
                    .clip(shape)
                    .background(
                        if (f) Brush.linearGradient(listOf(C.HiBg, C.HiBg))
                        else Brush.linearGradient(
                            listOf(
                                lerp(base, accent, if (C.dark) 0.62f else 0.34f),
                                lerp(base, accent, if (C.dark) 0.26f else 0.14f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(
                        str(R.string.shelf_all), 11.sp,
                        color = if (f) C.HiFg.copy(alpha = 0.72f) else ink.copy(alpha = 0.55f),
                        weight = FontWeight.SemiBold, maxLines = 1,
                    )
                    T(
                        Fmt.count(total), 34.sp,
                        color = if (f) C.HiFg else C.Fg,
                        weight = FontWeight.Bold, tight = true, maxLines = 1,
                    )
                }
            }
            /* 卡面不放底部标题（「全部」已经在卡里），但正文高度要和应用卡对齐：
               8dp 间距 + 12.5sp 标题行 = 24dp */
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 本周热门单行：横幅缩略 + 名称/副标 + 热度 */
@Composable
private fun HotRow(a: App, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val raw = art(a.art.banner, 480)
    val bmp = remember(raw) { if (blankArt(raw)) null else raw }
    /* 缩略图只有 104×58：先把横幅自带的留白裁掉，否则内置 logo 小得看不清 */
    val thumb = remember(bmp) { bmp?.let { trimIcon(it) } }
    val edges = remember(bmp) { bmp?.let { edgeColors(it) } ?: (Color(0xFF141821) to Color(0xFF141821)) }
    Focusable(onOpen, modifier) { f ->
        Row(
            Modifier.fillMaxSize().ring(f, 16.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(104.dp, 58.dp).clip(tileShape(58.dp))
                    .background(Brush.horizontalGradient(listOf(edges.first, edges.second))),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(
                        thumb ?: bmp, null, Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                    )
                } else NameCard(a)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                T(
                    a.name, 17.sp, color = if (f) C.HiFg else C.Fg,
                    weight = FontWeight.SemiBold, maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                T(
                    roomName(a.room) + " · " + a.version, 12.sp,
                    color = if (f) C.HiFg.copy(alpha = 0.7f) else C.Fg3, maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            T(Fmt.stars(a.stars), 13.sp, color = if (f) C.HiFg else C.Fg2)
        }
    }
}

/** 最近更新横幅卡：上图下文（名称在图标下方），无分类/版本 */
@Composable
private fun WideCard(
    a: App,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val raw = art(a.art.banner, 480)
    val bmp = remember(raw) { if (blankArt(raw)) null else raw }
    val edges = remember(bmp) { bmp?.let { edgeColors(it) } ?: (Color(0xFF141821) to Color(0xFF141821)) }
    Focusable(onOpen, modifier, requester = requester, onFocus = onFocus) { f ->
        Column(Modifier.fillMaxSize()) {
            /* 不画外包边：满图的卡边界交给投影与图片本身的边缘，加 1dp 灰线只会把图「框住」 */
            Box(
                Modifier.fillMaxWidth().weight(1f).ring(f, tileRadius(122.dp), 1.015f)
                    .cardShadow(tileShape(122.dp), 16.dp)
                    .clip(tileShape(122.dp))
                    .background(Brush.horizontalGradient(listOf(edges.first, edges.second))),
            ) {
                if (bmp != null) {
                    Image(
                        bmp, null, Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                    )
                } else NameCard(a)
                /* 热度胶囊 */
                Row(
                    Modifier.align(Alignment.TopEnd).padding(9.dp).height(21.dp)
                        .clip(CircleShape).background(Color(0x990A0D13))
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.Star, 10.dp, Color(0xFFF5B942))
                    Spacer(Modifier.width(3.dp))
                    T(Fmt.stars(a.stars), 9.5.sp, color = Color.White, weight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            T(
                a.name, 12.5.sp,
                color = if (f) C.Fg else C.Fg2,
                weight = FontWeight.SemiBold, maxLines = 1,
            )
        }
    }
}

/* ---------------- 主推位：轮播壳 + 交叉淡换内容 ----------------
   焦点节点只挂在壳上，Crossfade 只换内容：过渡的那半秒里两张内容同时存在，
   如果各自带 Focusable，会共用同一个 FocusRequester 互相抢焦点。 */
private const val HERO_INTERVAL_MS = 7_000L
private const val HERO_FADE_MS = 550
private const val HERO_WIDTH = 960

@Composable
private fun HeroCarousel(
    hero: List<App>,
    idx: Int,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    onOpen: (String) -> Unit,
) {
    if (hero.isEmpty()) return
    val active = Math.floorMod(idx, hero.size)

    Focusable(
        onClick = { onOpen(hero[Math.floorMod(idx, hero.size)].id) },
        modifier = modifier,
        requester = requester,
        onFocusChanged = onFocusChange,
    ) { f ->
        /* 主推卡 648×344，短边 344 走同一套 tileRadius（25% → 86，钳到上限 28），
           和下面 148/122 高的卡片落在同一个 28dp 上，整屏圆角只有一种语言。

           层级是防止「圆角亮边」的关键：裁切和压暗挂在同一层 card 上 ——
           `clip(shape)` 与 `drawWithContent` 里那道压暗矩形共用一个 canvas，
           矩形四周还各多铺 3px，圆角抗锯齿带里也一定有压暗，边缘不可能透出更亮的画面。
           文字、指示点画在 card 之上（也就是压暗之上），所以压暗可以把卡底压到近黑而不伤文字。
           之前压暗是 card 的兄弟节点、边界差 1px，那道亮弧就是从这条缝里漏出来的 */
        val r = tileRadius(344.dp)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .ring(f, r, 1.007f)
                    .cardShadow(tileShape(344.dp), 24.dp)
                    .clip(tileShape(344.dp))
                    .background(Color(0xFF12151C))
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color(0x1A05070B), 0.26f to Color.Transparent,
                                0.62f to Color.Transparent, 0.86f to Color(0x6605070B),
                                1f to Color(0xDD05070B),
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(-3f, -3f),
                            size = androidx.compose.ui.geometry.Size(size.width + 6f, size.height + 6f),
                        )
                        /* 卡底最后 1–2px 兜一层近乎不透明的底色：卡底本来就压到近黑，
                           这一层肉眼看不出，但能保证圆角抗锯齿带里不会残留 1 行满亮的画面
                           （实测最后一行会等于 banner 原色，就是用户看到的「亮边」） */
                        drawRect(
                            color = Color(0xF205070B),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2f),
                            size = androidx.compose.ui.geometry.Size(size.width, 2f),
                        )
                        /* 底段再沿圆角收一圈 1px 的同色边：圆角的抗锯齿带里，
                           画面的覆盖率会比压暗略高，最底下几像素会浮出一道浅弧。
                           这条只有底部 12% 才上色的内描边把它压平，上半段完全透明，
                           所以不会变成「一圈深色描边」 */
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.88f to Color.Transparent,
                                1f to Color(0x5905070B),
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                        )
                    },
            ) {
                Crossfade(targetState = active, animationSpec = tween(HERO_FADE_MS), label = "hero") { i ->
                    hero.getOrNull(i)?.let { HeroArt(it) }
                }
            }
            hero.getOrNull(active)?.let { HeroText(it) }
            HeroDots(hero.size, active)
        }
    }
}

/** 画面层：糊底铺满 + 整图 contain + 四边羽化。压暗统一在 card 层画，这里不碰 */
@Composable
private fun HeroArt(a: App) {
    val bmp = art(a.art.banner, HERO_WIDTH)
    /* 补底：同一张横幅的 32px 缩略图。整图必须 contain（不裁切），宽高比一不一致就会留白边，
       白边和图片之间是一条硬缝。用这张 32px 图 Crop 铺满当底，缝两侧都是同一张画的内容；
       32px 放大到 648 天然是糊的，不用 blur（blur 在 Android 9 真机上是空实现） */
    val bleed = art(a.art.banner, 32)
    val edges = remember(bmp) { bmp?.let { edgeColors(it) } ?: (Color(0xFF12151C) to Color(0xFF12151C)) }

    /* clipToBounds：ContentScale.Crop 的 Image 会画出自己的 bounds，不裁会盖到压暗层上 */
    Box(Modifier.fillMaxSize().clipToBounds()) {
        if (bleed != null) {
            Image(
                bleed, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
            )
            /* 糊底上再压一层同色渐变，保留原来「左主色 → 右主色」的色氛，不让糊图抢主体 */
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(edges.first.copy(alpha = 0.58f), edges.second.copy(alpha = 0.58f)),
                    ),
                ),
            )
        } else {
            /* 横幅完全没有时的兜底：满亮渐变。只在没图时出现，所以不会在卡边漏出亮弧 */
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(edges.first, edges.second)),
                ),
            )
        }
        if (bmp != null) {
            /* 按位图比例算出 Fit 之后图片**真正的矩形**（等价 contain），羽化贴在图片自己的四边上。
               贴在内框上不行：图片的边会落在羽化斜坡 76% 处，还留一条浅缝；
               只羽化有留白那条轴也不行：图片另一条轴贴着 12dp 内距，那里是「清晰图 → 糊底」的硬边。
               所以纵横各做一次 DstIn，alpha 相乘 —— 图片四条边都归零，任何一条边都不会成线 */
            BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
                val cw = maxWidth.value
                val ch = maxHeight.value
                val ir = bmp.width.toFloat() / bmp.height.toFloat()
                val cr = cw / ch
                val iw = if (ir > cr) cw else ch * ir
                val ih = if (ir > cr) cw / ir else ch
                Box(
                    Modifier.align(Alignment.Center).size(iw.dp, ih.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val f = 0.08f
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent, f to Color.Black,
                                    1f - f to Color.Black, 1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent, (f * 0.8f) to Color.Black,
                                    1f - f * 0.8f to Color.Black, 1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    Image(
                        bmp, null, Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High,
                    )
                }
            }
        } else NameCard(a)
    }
}

/** 名称 + 中文卖点：画在 card 之上，不受压暗影响 */
@Composable
private fun BoxScope.HeroText(a: App) {
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .padding(start = 26.dp, end = 142.dp, bottom = 26.dp),
    ) {
        T(a.name, 36.sp, weight = FontWeight.Bold, color = Color.White, tight = true, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        T(heroNote(a), 13.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 2)
    }
}

/** 轮播指示点：当前一张拉成 20×4 的胶囊，其余 5×4 圆点 */
@Composable
private fun BoxScope.HeroDots(count: Int, active: Int) {
    if (count < 2) return
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 27.dp).height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == active
            val w by animateDpAsState(if (on) 20.dp else 5.dp, tween(280), label = "dot")
            Box(
                Modifier.width(w).height(4.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (on) 0.92f else 0.36f)),
            )
        }
    }
}

/** 主推卡卖点：目录里的 note 是英文原文，10-foot 下按当前语言取一句短文案（见 values/hero_note_*） */
private fun heroNote(a: App): String = when (a.id) {
    "localsend-localsend" -> str(R.string.hero_note_localsend)
    "2dust-v2rayng" -> str(R.string.hero_note_v2rayng)
    "teamnewpipe-newpipe" -> str(R.string.hero_note_newpipe)
    "yuliskov-smarttube" -> str(R.string.hero_note_smarttube)
    "imranr98-obtainium" -> str(R.string.hero_note_obtainium)
    "yairm210-unciv" -> str(R.string.hero_note_unciv)
    "chen08209-flclash" -> str(R.string.hero_note_flclash)
    "shadowsocks-shadowsocks-android" -> str(R.string.hero_note_shadowsocks)
    "hiddify-hiddify-app" -> str(R.string.hero_note_hiddify)
    "lizongying-my-tv" -> str(R.string.hero_note_mytv)
    else -> roomName(a.room) + " · " + str(R.string.hero_note_free)
}

@Composable
private fun RowScope.FootDot() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.size(2.dp).background(Color.White.copy(alpha = 0.38f), CircleShape))
    Spacer(Modifier.width(8.dp))
}

/* ---------------- 房间页/搜索结果用的横排卡（带安装态） ---------------- */
@Composable
private fun PosterCard(
    a: App,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    requester: FocusRequester? = null,
) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val raw = art(a.art.banner, 480)
    /* 纯色占位横幅等于没有横幅 */
    val bmp = remember(raw) { if (blankArt(raw)) null else raw }
    val job = Installer.jobs[a.id]
    val installed = remember(a.id, tick) { Device.installedAs(ctx, a) }
    val apk = remember(a.id) { pickApk(a) }

    Focusable(onClick = onOpen, modifier = modifier, requester = requester) { f ->
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).ring(f, tileRadius(96.dp), 1.015f)
                    .cardShadow(tileShape(96.dp), 16.dp)
                    .clip(tileShape(96.dp)).background(C.Card),
            ) {
                if (bmp != null) {
                    Image(
                        bmp, null,
                        Modifier.fillMaxSize().graphicsLayer { scaleX = if (f) 1.03f else 1f; scaleY = scaleX },
                        contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                    )
                } else NameCard(a)
                JobBar(job)
                if (installed != null) {
                    Box(Modifier.padding(7.dp).size(6.dp).background(C.MeterFree, CircleShape).align(Alignment.TopStart))
                }
            }
            /* 名称常显（同首页 WideCard：12.5sp / SemiBold，未聚焦 C.Fg2，聚焦 C.Fg）；
               「· 房间 · 体积」只在聚焦时补出来，静息态保持和首页一样的简洁 */
            Row(
                Modifier.padding(top = 7.dp, start = 1.dp, end = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(
                    a.name, 12.5.sp,
                    color = if (f) C.Fg else C.Fg2,
                    weight = FontWeight.SemiBold, maxLines = 1,
                )
                if (f) {
                    Spacer(Modifier.width(5.dp))
                    T(
                        "· " + roomName(a.room) + " · " + Fmt.mb(apk.size), 11.5.sp,
                        color = C.Fg3, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobBar(job: Job?) {
    /* Held 不是进行中的任务，画成 8% 的细线会让人以为卡住了 */
    if (job == null || job is Job.Held) return
    val frac = (job as? Job.Downloading)?.fraction ?: if (job == Job.Installing) 0.9f else 0.08f
    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x29FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Color.White))
    }
}

/* ---------------- 分类 ---------------- */
@Composable
private fun BoxScope.CatsScreen(openRoom: (String) -> Unit, handoff: FocusHandoff) {
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(140); runCatching { firstTile.requestFocus() } }

    /* 一级页固定 12 个磁贴，顺序写死在 GROUP_KEYS 里：不按应用数排序，
       否则数据一变磁贴位置就跳，用户刚记住的位置全废。
       4 列 × 3 行、行高 132（3×132 + 2×16 = 428，从 130 起铺到 558）。
       「其它」是第 13 个入口，塞进 4 列会单独占一行、右边空三格，
       所以压成一张通栏矮卡（92 高，574..666）：卡片语言与上面 12 格完全一致，
       只是宽高比不同 —— 图标胶囊、标题、数量、取色渐变、圆角、聚焦环全部同一套。 */
    val groups = remember(Prefs.hideStreams) { GROUP_KEYS.map { it to appsOfGroup(it).size } }
    val otherCount = remember(Prefs.hideStreams) { appsOfGroup(GROUP_OTHER).size }
    val gap = 18.dp
    /* 一级页：顶栏在 4..52，页头 72..114，正文 130 起（与首页同一套纵向节奏） */
    Column(Modifier.at(0, 130).width(1152.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { (key, _) ->
                    RoomTile(
                        key = key,
                        openRoom = openRoom,
                        modifier = Modifier.weight(1f).height(132.dp),
                        requester = if (key == GROUP_KEYS.first()) firstTile else null,
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (otherCount > 0) {
            RoomTile(
                key = GROUP_OTHER,
                openRoom = openRoom,
                modifier = Modifier.fillMaxWidth().height(92.dp),
                wide = true,
            )
        }
    }

    PageHead(
        title = str(R.string.tab_categories),
        sub = str(R.string.cats_sub, Fmt.count(Catalog.apps.size)),
        hintKey = "OK",
        hint = str(R.string.hint_enter_room),
    )
}

/** 12 个一级分类的主色：磁贴底图过暗时拿它兜底，否则整格发黑看不出是分类 */
private fun groupAccent(k: String) = when (k) {
    "video" -> Color(0xFF8C4A6B)
    "photos" -> Color(0xFF3F6B8C)
    "music" -> Color(0xFF6A4A8C)
    "games" -> Color(0xFF5B4A9C)
    "reading" -> Color(0xFF7A5B3F)
    "social" -> Color(0xFF3F7A6B)
    "browser" -> Color(0xFF4A6B8C)
    "home" -> Color(0xFF2F6B7A)
    "desktop" -> Color(0xFF4A5A7A)
    "stores" -> Color(0xFF5A7A4A)
    "ai" -> Color(0xFF7A4A5B)
    "system" -> Color(0xFF5A5A5A)
    "other" -> Color(0xFF6E727C)
    else -> Color(0xFF7C8391)
}

/**
 * 一级分类磁贴。12 格是 4 列标准卡（274×132），「其它」是同一张卡的通栏版本（1152×92）：
 * 结构、配色、圆角、聚焦环都不变，只是横向排版并整体矮一截。
 */
@Composable
private fun RoomTile(
    key: String,
    openRoom: (String) -> Unit,
    modifier: Modifier,
    requester: FocusRequester? = null,
    wide: Boolean = false,
) {
    val top = remember(key, Prefs.hideStreams) { appsOfGroup(key).firstOrNull() }
    val bmp = art(top?.art?.banner, 32)
    /* 头号应用的横幅经常是深色图，取均值接近纯黑 → 磁贴整块发黑。
       亮度不够时换成该分类的主色，保证 12 个格子各有各的色相 */
    val tint = remember(bmp, key) {
        val m = bmp?.let { meanColor(it) }
        val luma = m?.let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue } ?: 0f
        if (m == null || luma < 0.16f) groupAccent(key) else m
    }

    Focusable({ openRoom(key) }, modifier, requester) { f ->
        Box(
            Modifier.fillMaxSize().ring(f, 15.dp, 1.02f)
                .cardShadow(RoundedCornerShape(15.dp), 18.dp)
                .clip(RoundedCornerShape(15.dp)).background(C.Card),
        ) {
            if (bmp != null) {
                Image(
                    bmp, null,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (f) 1.2f else 1.16f
                            scaleY = scaleX
                        },
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                )
            }
            /* 模糊：把 48px 的图放大到 336px，天然就是糊的。
               通栏卡 12:1，如果照搬 4 列卡的色标（54% 就到近黑），整条会变成一条死黑带，
               所以宽版把色相一直铺到 62% 才收，左边仍是最重的分类主色 */
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colorStops = if (wide) arrayOf(
                            0f to tint.copy(alpha = 0.58f),
                            0.62f to tint.copy(alpha = 0.16f),
                            1f to Color(0xF006080C),
                        ) else arrayOf(
                            0f to tint.copy(alpha = 0.62f),
                            0.54f to Color(0xCC06080C),
                            1f to Color(0xF006080C),
                        ),
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset.Infinite,
                    ),
                ),
            )
            Box(Modifier.fillMaxSize().background(Color(0x7004060A)))
            if (wide) {
                /* 通栏版：图标胶囊和标题仍各占一端（上/下），箭头放右侧中线，
                   与上面 12 格的「左上图标、左下标题」是同一套视觉锚点 */
                Box(
                    Modifier.fillMaxSize().padding(start = 14.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        GroupChip(key)
                        GroupTitle(key)
                    }
                    Ico(
                        Icons.ChevRight, 14.dp, Color.White.copy(alpha = 0.42f),
                        Modifier.align(Alignment.CenterEnd),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        GroupChip(key)
                        Ico(Icons.Play, 11.dp, Color.White.copy(alpha = 0.45f))
                    }
                    GroupTitle(key)
                }
            }
        }
    }
}

/** 磁贴左上角的图标胶囊：半透明玻璃底 + 1dp 描边，12 格与「其它」共用 */
@Composable
private fun GroupChip(key: String) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
            .background(Color(0x29FFFFFF)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) { Ico(groupIcon(key), 15.dp, Color.White) }
}

/** 磁贴左下角的标题 + 数量，两行共用同一套字号与透明度 */
@Composable
private fun GroupTitle(key: String) {
    Column {
        T(groupName(key), 13.5.sp, color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        T(
            str(R.string.tile_count, groupTotal(key), appsOfGroup(key).size), 9.5.sp,
            color = Color.White.copy(alpha = 0.64f), maxLines = 1,
        )
    }
}

/* ---------------- 房间（一级分类的详情页） ---------------- */
@Composable
private fun BoxScope.RoomScreen(
    room: String,
    last: String?,
    onFocusApp: (String) -> Unit,
    onBack: () -> Unit,
    openDetail: (String) -> Unit,
    handoff: FocusHandoff,
) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val apps = remember(room, Prefs.hideStreams) { appsOfGroup(room) }

    /* 二级分类（数据层的 `sub`，默认等于上游 room）：影视 = 在线视频 / 视频播放器，
       系统 = 工具 / VPN 与网络 / 文件与下载……只有一个 sub 的组没有细分价值，不挂 chips。
       数量按可见集合算，和网格、页头副标是同一个口径。 */
    val subs = remember(apps) {
        apps.groupBy { it.sub }
            .map { (sub, list) -> sub to list.size }
            .sortedByDescending { it.second }
    }
    /* 选中项跟着房间走：换一个分类进来回到「全部」，不要延续上一个分类的子分类 key */
    var pick by remember(room) { mutableStateOf<String?>(null) }
    val active = pick?.takeIf { p -> subs.any { it.first == p } }
    val shown = if (active == null) apps else apps.filter { it.sub == active }
    /* 筛选状态下按返回先清筛选、停在原地；再按一次才退出分类（根部的 BackHandler 接第二下） */
    BackHandler(enabled = active != null) { pick = null }

    val target = last?.takeIf { l -> shown.any { it.id == l } } ?: shown.firstOrNull()?.id
    @Suppress("UNUSED_VARIABLE") val watch = tick


    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        /* 页头 0..44，网格顶边提到 56、内部 top padding 12，卡片视觉上仍在 68 起
           （原来 96，上方空了 52dp）。卡片是 16:9，高度被宽度钉死；每格还有 7dp 间距 +
           12.5sp 名称（≈23dp），所以「一格」实际高 ≈ 宽×9/16 + 23。
           4 列 × 3 行：卡宽 (1152-3×18)/4 = 274.5、高 154.4，一格 177.7，
           三行 533 + 2×22 = 577，刚好铺满 592。之前 6 列（177 宽）一屏塞 24 张，
           缩略图小到看不清；也不能取 5 列 —— 5 列 216 宽时一格 145，
           四行 580+ 装不进 592，三行又会在底部空出 100dp 的带状留白。
           顶边多出的 12 是给聚焦环留的：ring 的 halo（gap 2 + halo 7）画在卡片 bounds 之外，
           而 LazyVerticalGrid 会沿滚动方向裁掉越界内容，不留缝第一行的白环就会被削平 */
        modifier = Modifier.at(0, 56).width(1152.dp).height(592.dp),
        contentPadding = PaddingValues(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                T(str(R.string.room_empty), 12.sp, Modifier.padding(top = 10.dp), color = C.Fg3)
            }
        }
        items(shown, key = { it.id }) { a ->
            PosterCard(
                a,
                Modifier.fillMaxWidth(),
                onOpen = { openDetail(a.id) },
                requester = if (a.id == target) handoff.target else null,
            )
        }
    }

    /* 二级 chips 挂在页头右端（同一行 44 高，不占网格空间）：
       「全部」+ 该分类下的每个 sub，选中的换成实心低调胶囊，聚焦整颗反色。 */
    val chips: (@Composable () -> Unit)? = if (subs.size > 1) {
        {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubChip(
                    label = str(R.string.sub_all),
                    count = apps.size,
                    selected = active == null,
                ) { pick = null }
                subs.forEach { (sub, n) ->
                    SubChip(
                        label = roomName(sub),
                        count = n,
                        selected = active == sub,
                    ) { pick = sub }
                }
            }
        }
    } else null

    SubHead(
        title = groupName(room),
        sub = str(R.string.group_sub, shown.size, groupTotal(room)),
        onBack = onBack,
        trailing = chips,
    )
}

/** 二级分类胶囊（房间页页头右端）：30 高、全圆角，文字 + 数量。
 *  三态：静息 `C.RowBg` + `C.Line` 描边；选中（当前筛选）`C.Fg` 半透明实心、不描边；
 *  聚焦整颗反色 `C.HiBg` / `C.HiFg`。和顶部导航的「选中 / 聚焦」是同一套语言。 */
@Composable
private fun SubChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Focusable(onClick, Modifier.height(30.dp)) { f ->
        Row(
            Modifier.fillMaxHeight().clip(CircleShape)
                .background(
                    when {
                        f -> C.HiBg
                        selected -> C.Fg.copy(alpha = if (C.dark) 0.30f else 0.22f)
                        else -> C.RowBg
                    },
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (f || selected) Color.Transparent else C.Line,
                    CircleShape,
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(
                label, 11.5.sp,
                color = if (f) C.HiFg else if (selected) C.Fg else C.Fg2,
                weight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            T(
                Fmt.count(count), 10.5.sp,
                color = if (f) C.HiFg.copy(alpha = 0.60f) else C.Fg3,
                maxLines = 1,
            )
        }
    }
}

/* ---------------- 我的 ---------------- */
@Composable
private fun BoxScope.MineScreen(
    last: String?,
    onFocusApp: (String) -> Unit,
    openDetail: (String) -> Unit,
    openSettings: () -> Unit,
    toast: (String) -> Unit,
    handoff: FocusHandoff,
) {
    val ctx = LocalContext.current
    val tick = Device.tick
    /* 索引到达（或换了一轮）后要重算「可更新」：epoch 不进 key 的话，
       启动时先渲染一遍「都是最新」，索引到了界面也不动 */
    val updateEpoch = UpdateIndex.epoch
    val mine = remember(tick, updateEpoch) {
        Catalog.apps.groupBy { it.pkg }.mapNotNull { (pkg, list) ->
            Device.installed(ctx, pkg)?.let { i ->
                (list.firstOrNull { it.certSha256 != null && it.certSha256 in i.certs }
                    ?: list.firstOrNull { it.forkOf == null } ?: list.first()) to i
            }
        }
    }
    /* 两个来源：catalog 里记录的版本比机器上装的新（发版时就知道），
       或者更新索引拿到了比 catalog 更新的 Release（不发版也能知道） */
    fun needsUpdate(a: App, i: Installed) = i.versionCode < a.versionCode || UpdateIndex.isNewer(a)
    val updatable = mine.filter { (a, i) -> needsUpdate(a, i) }
    var others by remember { mutableStateOf<List<LocalApp>?>(null) }
    LaunchedEffect(tick) { others = withContext(Dispatchers.IO) { otherApps(ctx, mine.map { it.first.pkg }.toSet()) } }
    val free = remember(tick) { Device.freeBytes(ctx) }
    val total = remember(tick) { Device.totalBytes(ctx) }
    val cache = remember(tick) { Device.cacheBytes(ctx) }
    val used = (total - free).coerceAtLeast(0L)
    val appsPart = (used - cache).coerceAtLeast(0L)


    Column(Modifier.at(0, 120).width(592.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val shown = mine.take(5)
        if (shown.isEmpty()) {
            T(str(R.string.mine_empty), 12.sp, Modifier.padding(vertical = 8.dp), color = C.Fg3)
        }
        shown.forEach { (a, i) ->
            InstRow(
                a = a,
                ver = i.versionName ?: "",
                updatable = needsUpdate(a, i),
                progress = (Installer.jobs[a.id] as? Job.Downloading)?.fraction,
                onClick = { openDetail(a.id) },
                onFocus = { onFocusApp(a.id) },
                requester = if (a.id == (last ?: shown.first().first.id)) handoff.target else null,
            )
        }
        if (others != null && others!!.isNotEmpty()) {
            T(
                str(R.string.section_other), 10.5.sp,
                Modifier.padding(start = 2.dp, top = 6.dp), color = C.Fg3, weight = FontWeight.Medium,
            )
            others!!.take(2).forEach { o ->
                OtherRow(o) { launch(ctx, o.pkg) }
            }
        }
    }

    Column(Modifier.at(728, 120).width(424.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StoreCard(used = used, apps = appsPart, cache = cache, free = free, total = total)
        DarkBtn(
            text = str(R.string.update_all_n, updatable.size),
            enabled = updatable.isNotEmpty(),
            onClick = {
                updatable.forEach { (a, _) -> Installer.start(ctx, a) }
                toast(str(R.string.toast_updated_all, updatable.size))
            },
        )
        SmallRow(
            title = str(R.string.queue_title),
            sub = str(R.string.queue_sub, Installer.jobs.count { it.value is Job.Downloading }),
            value = Installer.jobs.size.toString(),
            onClick = { toast(str(R.string.toast_queue)) },
        )
        SmallRow(
            title = str(R.string.fav_title),
            sub = str(R.string.fav_sub),
            value = "0",
            onClick = { toast(str(R.string.toast_fav)) },
        )
        SmallRow(
            title = str(R.string.settings_title),
            sub = str(R.string.settings_sub),
            value = "›",
            onClick = openSettings,
        )
    }

    PageHead(
        title = str(R.string.mine_title),
        sub = str(R.string.mine_sub, mine.size, updatable.size),
    )
}

@Composable
private fun InstRow(
    a: App,
    ver: String,
    updatable: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    requester: FocusRequester? = null,
) {
    Focusable(onClick, requester = requester, onFocus = onFocus) { f ->
        Row(
            Modifier.fillMaxWidth().height(60.dp).ring(f, 15.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(a, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(a.name, 13.5.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
                T(
                    ver + " · " + roomName(a.room) + " · " + Fmt.mb(pickApk(a).size), 10.5.sp,
                    color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.6f), maxLines = 1,
                )
            }
            if (progress != null) {
                T("${(progress * 100).toInt()}%", 11.sp, color = if (f) C.HiFg else C.Fg2, weight = FontWeight.Medium)
            } else if (updatable) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (f) Color(0x3DFFFFFF) else Color(0x42E9A03C))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    T(
                        str(R.string.badge_update), 9.5.sp,
                        color = if (f) C.HiFg else (if (C.dark) Color(0xFFFFD8A0) else Color(0xFF8A5A12)),
                        weight = FontWeight.Bold,
                    )
                }
            } else {
                T(str(R.string.badge_latest), 11.sp, color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.62f))
            }
        }
    }
}

@Composable
private fun OtherRow(o: LocalApp, onClick: () -> Unit) {
    Focusable(onClick) { f ->
        Row(
            Modifier.fillMaxWidth().height(52.dp).ring(f, 14.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(tileShape(30.dp)).background(C.Fg.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                if (o.icon != null) FitIcon(o.icon, 30.dp, fill = 0.84f)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(o.label, 13.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
                T(
                    listOfNotNull(o.version, str(R.string.not_in_list)).joinToString(" · "), 10.5.sp,
                    color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.6f), maxLines = 1,
                )
            }
            T(str(R.string.action_open), 11.sp, color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.62f))
        }
    }
}

@Composable
private fun StoreCard(used: Long, apps: Long, cache: Long, free: Long, total: Long) {
    val denom = used + free
    fun frac(v: Long) = if (denom > 0) (v.toFloat() / denom).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.RowBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            T(str(R.string.storage_local), 11.sp, color = C.Fg3, weight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.Bottom) {
                T(Fmt.gbNum(used), 22.sp, weight = FontWeight.Bold, tight = true)
                T(" / " + Fmt.gb(denom), 11.sp, Modifier.padding(start = 3.dp, bottom = 3.dp), color = C.Fg3)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33808080))) {
            Box(Modifier.fillMaxHeight().weight(frac(apps).coerceAtLeast(0.01f)).background(C.MeterApp))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.fillMaxHeight().weight(frac(cache).coerceAtLeast(0.01f)).background(C.MeterCache))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.fillMaxHeight().weight(frac(free).coerceAtLeast(0.01f)).background(C.MeterFree))
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Leg(C.MeterApp, str(R.string.storage_apps, Fmt.gb(apps)))
            Leg(C.MeterCache, str(R.string.storage_cache, Fmt.gb(cache)))
            Leg(C.MeterFree, str(R.string.storage_free, Fmt.gb(free)))
        }
    }
}

@Composable
private fun Leg(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(c, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        T(text, 10.sp, color = C.Fg3, maxLines = 1)
    }
}

@Composable
private fun SmallRow(title: String, sub: String, value: String, onClick: () -> Unit) {
    Focusable(onClick) { f ->
        Row(
            Modifier.fillMaxWidth().height(54.dp).ring(f, 15.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T(title, 13.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
                T(sub, 10.5.sp, color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.58f), maxLines = 1)
            }
            T(value, 11.sp, color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.62f))
        }
    }
}

/* ---------------- 搜索 ---------------- */
@Composable
private fun BoxScope.SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    openDetail: (String) -> Unit,
    onBack: () -> Unit,
    handoff: FocusHandoff,
) {
    val q = query.trim()
    val hits = remember(q, Prefs.hideStreams) {
        if (q.isEmpty()) emptyList()
        else Catalog.visible.filter { a ->
            (a.name + " " + roomName(a.room) + " " + a.maker + " " + a.desc)
                .lowercase().contains(q.lowercase())
        }.sortedByDescending { it.stars }
    }

    Focusable(
        onClick = { onTyping(true) },
        modifier = Modifier.at(0, 72).width(596.dp).height(46.dp),
        requester = handoff.target,
    ) { f ->
        Row(
            Modifier.fillMaxSize().ring(f || typing, 13.dp, 1.006f)
                .background(C.RowBg, RoundedCornerShape(13.dp))
                .border(1.dp, if (f || typing) C.Line2 else C.Line, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Icons.Search, 17.dp, C.Fg.copy(alpha = 0.55f))
            Spacer(Modifier.width(10.dp))
            T(
                if (q.isEmpty()) str(R.string.search_ph) else query, 14.sp,
                color = if (q.isEmpty()) C.Fg3 else C.Fg,
                weight = if (q.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
            )
            if (typing) {
                Spacer(Modifier.width(3.dp))
                BlinkCaret()
            }
        }
    }

    if (q.isNotEmpty()) {
        LazyColumn(
            Modifier.at(0, 132).width(596.dp).height(330.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hits.isEmpty()) {
                item { T(str(R.string.search_none, q), 12.sp, Modifier.padding(top = 8.dp), color = C.Fg3) }
            }
            items(hits.take(5), key = { it.id }) { a ->
                SearchRow(a) { openDetail(a.id) }
            }
        }
    } else {
        Column(Modifier.at(0, 132).width(596.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            hotWords().take(5).forEachIndexed { i, w ->
                KeywordRow(w, recentTimes().getOrElse(i) { "" }) { onQuery(w) }
            }
        }
        T(
            str(R.string.search_kb_tip), 11.sp,
            Modifier.at(0, 472).width(500.dp), color = C.Fg3,
        )
    }

    // 软键盘
    val rows = listOf("ABCDE", "FGHIJ", "KLMNO", "PQRST", "UVWXY")
    Column(Modifier.at(748, 120).width(404.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { ch -> Key(ch.toString(), Modifier.weight(1f)) { onQuery(query + ch) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Key("Z", Modifier.weight(1f)) { onQuery(query + "Z") }
            Key(str(R.string.key_space), Modifier.weight(2f), small = true) { onQuery("$query ") }
            Key(str(R.string.key_del), Modifier.weight(2f), small = true) {
                onQuery(query.dropLast(1))
            }
        }
    }
    Column(Modifier.at(748, 466).width(404.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        hotWords().take(4).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { w -> HotChip(w, Modifier.weight(1f)) { onQuery(w) } }
            }
        }
    }

    SubBack(onBack)
}

private fun hotWords() = strArray(R.array.hot_words)

@Composable
private fun BlinkCaret() {
    Box(Modifier.size(2.dp, 18.dp).background(C.Fg))
}

@Composable
private fun SearchRow(a: App, onClick: () -> Unit) {
    Focusable(onClick) { f ->
        Row(
            Modifier.fillMaxWidth().height(60.dp).ring(f, 15.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(a, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(a.name, 13.5.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
                T(
                    roomName(a.room) + " · " + Fmt.mb(pickApk(a).size) + " · " + a.version, 10.5.sp,
                    color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.6f), maxLines = 1,
                )
            }
            T(Fmt.stars(a.stars) + " ★", 11.sp, color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.62f))
        }
    }
}

@Composable
private fun KeywordRow(word: String, whenText: String, onClick: () -> Unit) {
    Focusable(onClick) { f ->
        Row(
            Modifier.fillMaxWidth().height(54.dp).ring(f, 14.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T(word, 13.5.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
                T(
                    str(R.string.recent_search, whenText), 10.5.sp,
                    color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.6f), maxLines = 1,
                )
            }
            Ico(Icons.Search, 14.dp, (if (f) C.HiFg else C.Fg).copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    Focusable(onClick, modifier) { f ->
        Box(
            Modifier.fillMaxWidth().height(46.dp).ring(f, 11.dp, 1.04f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(11.dp))
                .border(1.dp, if (f) C.HiBg else C.Line, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            T(
                label, if (small) 11.5.sp else 14.sp,
                color = if (f) C.HiFg else C.Fg2, weight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HotChip(word: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick, modifier) { f ->
        Box(
            Modifier.fillMaxWidth().height(38.dp).ring(f, 19.dp, 1.03f)
                .background(if (f) C.HiBg else C.RowBg, CircleShape)
                .border(1.dp, if (f) C.HiBg else C.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { T(word, 11.5.sp, color = if (f) C.HiFg else C.Fg2, weight = FontWeight.Medium, maxLines = 1) }
    }
}

/* ---------------- 详情 ---------------- */
/* ============================================================
   详情页 v10 —— 内容重组版
   ① 去顶栏，返回在左上  ② 更新脉络时间线  ③ 横幅垫底整图不裁
   ④ 签名校验从标签升为右栏主角
   坐标：Panel 已内缩 64/30，所以这里 (0,0) 即原型的 (64,30)
   ============================================================ */
/**
 * 详情页两栏的共用基线：header 区（左：图标/名字/标签/简介/时间线；右：安装/事实/签名）
 * 都锁在这个高度里，后面的「横幅预览」和「同房间」就从同一条 y 起，视觉上顶部齐平。
 * 取 384 是给简介两行 + 四条时间线留足余量；两栏自然高度都小于它，多出来的是底部留白。
 */
private val DETAIL_HEAD = 384.dp

@Composable
private fun BoxScope.DetailScreen(
    app: App,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    handoff: FocusHandoff,
) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val rawArt = art(app.art.banner, 960)
    /* 纯色占位横幅（SkyTube 的 banner_default 就是纯 #222）当没有横幅 */
    val bmp = remember(rawArt) { if (blankArt(rawArt)) null else rawArt }
    val edges = remember(bmp) { bmp?.let { edgeColors(it) } ?: (Color(0xFF12151C) to Color(0xFF12151C)) }
    val installed = remember(app.id, tick) { Device.installedAs(ctx, app) }
    /* 索引里有新版时，详情页整页都用「最新那一版」的数据：版本号、下载体积、
       版本脉络、安装按钮都跟着变，但签名仍按 catalog 记录的 certSha256 校验 */
    val live = UpdateIndex.effective(app)
    val apk = remember(live.id, live.version) { pickApk(live) }
    val job = Installer.jobs[app.id]
    val rel = live.releases.take(4)
    val updatable = installed != null &&
        (installed.versionCode < app.versionCode || UpdateIndex.isNewer(app))
    val ok = okColor()

    /* ---------- 顶栏：返回胶囊 + 面包屑 ---------- */
    Row(Modifier.at(0, 0).width(1152.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        BackPill(onBack)
        Spacer(Modifier.width(20.dp))
        T(roomName(app.room), 12.5.sp, color = C.Fg2, weight = FontWeight.Medium, maxLines = 1)
        Box(Modifier.padding(horizontal = 10.dp).size(3.dp).background(C.Fg3, CircleShape))
        T(app.name, 12.5.sp, color = C.Fg3, maxLines = 1)
    }

    Row(Modifier.at(0, 70).width(1152.dp).height(584.dp)) {
        /* ===== 左栏：身份 → 标签 → 简介 → 更新脉络 → 横幅预览 ===== */
        Column(Modifier.width(712.dp).fillMaxHeight()) {
            /* 前段固定高度：右栏「同房间」要从同一条 y 开始，才能和下面横幅预览的顶部齐平 */
            Column(Modifier.height(DETAIL_HEAD)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(168.dp, 94.dp).cardShadow(tileShape(94.dp), 17.dp)
                        .clip(tileShape(94.dp))
                        .background(Brush.horizontalGradient(listOf(edges.first, edges.second))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bmp, null, Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                        )
                    } else NameCard(app)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    T(app.name, 36.sp, weight = FontWeight.Bold, tight = true, maxLines = 1)
                    Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(
                            app.maker + " / " + app.repo.substringAfter('/'), 12.5.sp,
                            color = C.Fg3, maxLines = 1,
                        )
                        DetailDot()
                        Ico(Icons.Star, 13.dp, Color(0xFFE7B24C))
                        Spacer(Modifier.width(5.dp))
                        T(
                            Fmt.stars(app.stars), 12.5.sp,
                            color = Color(0xFFE7B24C), weight = FontWeight.SemiBold, maxLines = 1,
                        )
                        DetailDot()
                        T(
                            if (app.archived) str(R.string.detail_archived) else str(R.string.detail_maintained),
                            12.5.sp, color = C.Fg3, maxLines = 1,
                        )
                    }
                }
            }

            /* 标签：房间 / 许可证 / 架构，星标与签名不再重复出现 */
            Row(
                Modifier.padding(top = 17.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailTag(roomName(app.room), key = true, dot = chipColor(app.room))
                DetailTag(app.licence ?: str(R.string.licence_unknown))
                DetailTag(if (apk.abi in listOf("any", "universal")) str(R.string.tag_universal) else abiLabel(apk.abi))
            }

            T(
                app.desc.ifBlank { app.note }, 13.5.sp,
                Modifier.padding(top = 17.dp).width(676.dp), color = C.Fg2, maxLines = 2,
            )

            /* 更新脉络：版本号 + 日期 + 间隔，体现维护健康度 */
            Column(Modifier.padding(top = 22.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(str(R.string.detail_history), 10.5.sp, color = C.Fg3, weight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    T(cadence(live.releases), 10.5.sp, color = C.Fg3)
                }
                Spacer(Modifier.height(11.dp))
                rel.forEachIndexed { i, r ->
                    TimelineRow(r, now = i == 0, last = i == rel.lastIndex, total = app.releases.size)
                }
            }
            }

            /* 横幅预览：整图 contain + 左右羽化，图上不加任何文字 */
            Box(
                Modifier.fillMaxWidth().weight(1f).heightIn(min = 184.dp)
                    .cardShadow(tileShape(184.dp), 24.dp)
                    .clip(tileShape(184.dp))
                    .background(Brush.horizontalGradient(listOf(edges.first, edges.second))),
            ) {
                if (bmp != null) {
                    MaskedArt(bmp, maskStart = 0.10f, maskEnd = 0.90f)
                } else NameCard(app)
            }
        }

        Spacer(Modifier.width(40.dp))

        /* ===== 右栏：动作 → 事实 → 信任 → 推荐 ===== */
        Column(Modifier.width(400.dp).fillMaxHeight()) {
            /* 与左栏共用 DETAIL_HEAD：推免区块的顶部因此和横幅预览顶部齐平 */
            Column(Modifier.height(DETAIL_HEAD)) {
            DetailCta(
                /* 文案用 catalog 里的原始 app 判「有没有更新」：live 的 version 已经是新版，
                   拿它去比永远相等，会把「更新」误判成「打开」 */
                text = installLabel(app, installed, job),
                version = live.version,
                icon = if (installed != null && !updatable) Icons.Play else Icons.Down,
                progress = (job as? Job.Downloading)?.fraction,
                requester = handoff.target,
            ) { installOrOpen(ctx, app) }

            /* 预检拦下来的「签名不同」必须有出口：之前只有 Job.Held 状态、没有界面，
               用户看到的是点更新后一条卡住的细进度线，既没解释也装不上。 */
            val hasOld = installed != null
            when (job) {
                is Job.Held -> JobDialog(
                    title = str(R.string.held_title),
                    body = job.message,
                    note = if (hasOld) str(R.string.held_uninstall_note) else null,
                    primary = if (hasOld) str(R.string.held_uninstall) else str(R.string.held_try),
                    onPrimary = {
                        if (hasOld) Installer.uninstallThenInstall(ctx, app) else Installer.start(ctx, app, force = true)
                    },
                    secondary = if (hasOld) str(R.string.held_try) else str(R.string.held_close),
                    /* 已装旧版时第二键是「仍然尝试」：跳过预检交给系统，系统拒绝后走下面的 Failed 弹层 */
                    onSecondary = {
                        if (hasOld) Installer.start(ctx, app, force = true) else Installer.clear(app.id)
                    },
                    onDismiss = { Installer.clear(app.id) },
                )
                is Job.Failed -> JobDialog(
                    title = str(R.string.failed_title),
                    body = job.message,
                    note = null,
                    primary = str(R.string.held_close),
                    onPrimary = { Installer.clear(app.id) },
                    secondary = null,
                    onSecondary = null,
                    onDismiss = { Installer.clear(app.id) },
                )
                else -> Unit
            }

            if (installed != null) {
                Spacer(Modifier.height(8.dp))
                GhostBtn(str(R.string.uninstall), Icons.Trash) { uninstall(ctx, app.pkg) }
            }

            /* 事实：去掉卡片壳，只留分隔线 + 标签/值。
               水平内边距 10dp 与下方「同房间」标题、推荐行的内容区对齐（卡片类容器仍满宽） */
            Column(Modifier.padding(top = 18.dp).fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .height(1.dp).background(C.Line),
                )
                Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp)) {
                    Fact(str(R.string.meta_size), Fmt.mb(apk.size), Modifier.weight(1f))
                    Spacer(Modifier.width(24.dp))
                    Fact(str(R.string.meta_updated), Fmt.date(live.date), Modifier.weight(1f))
                }
                Row(Modifier.padding(horizontal = 10.dp)) {
                    Fact(str(R.string.fact_version), live.version, Modifier.weight(1f))
                    Spacer(Modifier.width(24.dp))
                    Fact(str(R.string.fact_licence), app.licence ?: str(R.string.licence_unknown), Modifier.weight(1f))
                }
            }

            /* 信任：右栏唯一的强调卡 */
            val signed = app.certSha256 != null
            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.RowBg)
                    .border(1.dp, if (signed) ok.copy(alpha = 0.26f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).background(ok.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Ico(if (signed) Icons.Check else Icons.Shield, 14.dp, ok) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    T(
                        if (signed) str(R.string.detail_signed_ok) else str(R.string.detail_signed_no),
                        12.5.sp, weight = FontWeight.SemiBold, maxLines = 1,
                    )
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(Icons.Git, 12.dp, C.Fg3)
                        Spacer(Modifier.width(5.dp))
                        T(str(R.string.detail_source), 10.5.sp, color = C.Fg3, maxLines = 1)
                    }
                }
            }
            }

            /* 推荐：细线列表，聚焦时才浮出胶囊。
               区块撑满基线以下的高度（与左栏横幅等长）：标题贴顶、列表贴底，
               多出来的空间自动落在标题与第一行之间，上下两端都跟横幅对齐 */
            val sameCnt = Catalog.apps.count { it.room == app.room && it.id != app.id }
            val more = moreOf(app)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                /* 与 MoreRow 的 10dp 行内边距对齐：标题和右侧数量都跟列表内容同一竖线 */
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(
                        if (sameCnt >= 3) str(R.string.detail_same_room, roomName(app.room))
                        else str(R.string.detail_more),
                        10.5.sp, color = C.Fg3, weight = FontWeight.Medium, maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    T(
                        str(
                            R.string.detail_count,
                            Fmt.count(if (sameCnt >= 3) roomTotal(app.room) else more.size),
                        ),
                        10.5.sp, color = C.Fg3, maxLines = 1,
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    more.forEach { m -> MoreRow(m) { onOpen(m.id) } }
                }
            }
        }
    }
}

/** 原型 .chrome 的返回胶囊：44 高、圆角 999。所有子页左上统一用它 */
@Composable
private fun BackPill(onBack: () -> Unit) {
    Focusable(onBack) { f ->
        Row(
            Modifier.height(44.dp).ring(f, 22.dp, 1.02f)
                /* 不描边：胶囊靠 RowBg 的填充差（深色）/ cardShadow（浅色）定边，
                   和全站卡片同一套语言，见 DESIGN-SYSTEM 6 */
                .cardShadow(CircleShape, 10.dp)
                .background(if (f) C.HiBg else C.RowBg, CircleShape)
                .padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Icons.ChevLeft, 16.dp, if (f) C.HiFg else C.Fg)
            Spacer(Modifier.width(9.dp))
            T(str(R.string.back), 13.5.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** 子页左上角的返回：统一贴在 (0,0)，44 高，与详情页 chrome 对齐。
 *  刚进页面时先不可聚焦，把首帧的自动焦点让给内容首项；160ms 后再允许聚焦（不会反抢） */
@Composable
private fun BoxScope.SubBack(onBack: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(160); armed = true }
    Box(
        Modifier.at(0, 0).height(44.dp)
            .focusProperties { canFocus = armed },
    ) { BackPill(onBack) }
}

/** 一级页（分类 / 我的应用）的页头：顶栏在 4..52，页头贴在 72，只有标题 + 副标，
 *  右侧可选按键提示。没有返回钮——这两个页面由顶栏导航直达。 */
@Composable
private fun BoxScope.PageHead(
    title: String,
    sub: String,
    hintKey: String? = null,
    hint: String? = null,
) {
    Box(Modifier.at(0, 72).width(1152.dp).height(42.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.Bottom) {
            T(title, 23.sp, weight = FontWeight.Bold, tight = true)
            Spacer(Modifier.width(12.dp))
            T(sub, 11.5.sp, Modifier.padding(bottom = 4.dp), color = C.Fg3, maxLines = 1)
        }
        if (hintKey != null && hint != null) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(C.RowBg)
                        .border(1.dp, C.Line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) { T(hintKey, 10.sp, color = C.Fg2, weight = FontWeight.SemiBold) }
                Spacer(Modifier.width(7.dp))
                T(hint, 11.sp, color = C.Fg3)
            }
        }
    }
}

/** 子页页头：左上「‹ 返回」+ 紧随其后的标题 / 副标。页头只占 0..44 一条。
 *  不在右上挂按键提示：返回靠左上胶囊，键盘 ESC 静默可用。
 *  焦点语义同 SubBack：进页面先不可聚焦，160ms 后才可聚焦，首帧焦点仍归内容首项。
 *  位置是绝对坐标，所以各页把它放在组合顺序最后调用即可（和 SubBack 一致）。 */
@Composable
private fun BoxScope.SubHead(
    title: String,
    sub: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(160); armed = true }
    Row(
        Modifier.at(0, 0).width(1152.dp).height(44.dp)
            .focusProperties { canFocus = armed },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackPill(onBack)
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            T(title, 23.sp, weight = FontWeight.Bold, tight = true)
            Spacer(Modifier.width(12.dp))
            T(sub, 11.5.sp, Modifier.padding(bottom = 3.dp), color = C.Fg3, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        /* 右端插槽：房间页把二级分类胶囊放这里，与标题同一行，不额外占纵向空间 */
        trailing?.invoke()
    }
}

/** 标题区的小圆点分隔符 */
@Composable
private fun DetailDot() {
    Box(Modifier.padding(horizontal = 9.dp).size(3.dp).background(C.Fg3.copy(alpha = 0.55f), CircleShape))
}

/** 标签：27 高、圆角 8、11px */
@Composable
private fun DetailTag(text: String, key: Boolean = false, dot: Color? = null) {
    Row(
        Modifier.height(27.dp).clip(RoundedCornerShape(8.dp))
            .background(if (key) C.Fg.copy(alpha = 0.07f) else C.RowBg)
            .border(1.dp, if (key) Color.Transparent else C.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Spacer(Modifier.width(7.dp))
        }
        T(text, 11.sp, color = if (key) C.Fg2 else C.Fg2, weight = FontWeight.Medium, maxLines = 1)
    }
}

/** 时间线一行：左侧圆点 + 连接线，最新一版带绿点与「N 天前」 */
@Composable
private fun TimelineRow(r: Release, now: Boolean, last: Boolean, total: Int) {
    val ok = okColor()
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(11.dp).fillMaxHeight()) {
            if (!last) {
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = 21.dp)
                        .width(1.dp).height(27.dp).background(C.Fg3.copy(alpha = 0.32f)),
                )
            }
            if (now) {
                Box(Modifier.align(Alignment.Center).size(15.dp).background(ok.copy(alpha = 0.16f), CircleShape))
            }
            Box(
                Modifier.align(Alignment.Center).size(11.dp)
                    .background(if (now) ok else (if (C.dark) Color(0xFF07090D) else Color(0xFFEEF1F6)), CircleShape)
                    .border(if (now) 0.dp else 2.dp, if (now) Color.Transparent else C.Fg3, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        T(
            r.tag, 12.5.sp, Modifier.width(74.dp),
            color = if (now) C.Fg else C.Fg2, weight = FontWeight.SemiBold, maxLines = 1,
        )
        T(Fmt.shortDate(r.date), 11.5.sp, color = C.Fg3, maxLines = 1)
        if (now) {
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier.height(18.dp).clip(RoundedCornerShape(5.dp))
                    .background(ok.copy(alpha = 0.14f)).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(
                    str(R.string.detail_now, daysSince(r.date)), 9.5.sp,
                    color = ok, weight = FontWeight.SemiBold, maxLines = 1,
                )
            }
        }
        if (last) {
            Spacer(Modifier.weight(1f))
            T(str(R.string.detail_total, total), 10.5.sp, color = C.Fg3, maxLines = 1)
        }
    }
}

/** 右栏主按钮：66 高、圆角 16、白底黑字，右侧带版本号 */
@Composable
private fun DetailCta(
    text: String,
    version: String,
    icon: ImageVector,
    progress: Float?,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    Focusable(onClick, requester = requester) { f ->
        Row(
            Modifier.fillMaxWidth().height(66.dp).ring(f, 16.dp, 1.008f)
                .background(C.BtnBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp).background(C.BtnFg, CircleShape), contentAlignment = Alignment.Center) {
                if (progress != null) Ring(progress, C.BtnBg, C.BtnFg.copy(alpha = 0.3f), 14.dp)
                else Ico(icon, 12.dp, C.BtnBg)
            }
            Spacer(Modifier.width(12.dp))
            T(text, 15.5.sp, color = C.BtnFg, weight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            T(version, 12.sp, color = C.BtnFg.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}

/** 事实项：无卡片壳，只有标签 + 值 */
@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 7.dp)) {
        T(label, 9.5.sp, color = C.Fg3, maxLines = 1)
        T(value, 14.sp, Modifier.padding(top = 5.dp), weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 推荐行：64×36 横幅 + 名称/副标 + 箭头，聚焦变白底 */
@Composable
private fun MoreRow(a: App, onOpen: () -> Unit) {
    val b = art(a.art.banner, 320)
    val thumb = remember(b) { b?.let { trimIcon(it) } }
    val edges = remember(b) { b?.let { edgeColors(it) } ?: (Color(0xFF141821) to Color(0xFF141821)) }
    val apk = remember(a.id) { pickApk(a) }
    Focusable(onOpen) { f ->
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).ring(f, 12.dp, 1.005f)
                    .background(if (f) C.HiBg else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(64.dp, 36.dp).clip(tileShape(36.dp))
                        .background(Brush.horizontalGradient(listOf(edges.first, edges.second))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (b != null) {
                        Image(
                            thumb ?: b, null, Modifier.fillMaxSize().padding(2.dp),
                            contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                        )
                    } else NameCard(a)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(
                        a.name, 13.sp, color = if (f) C.HiFg else C.Fg,
                        weight = FontWeight.SemiBold, maxLines = 1,
                    )
                    T(
                        roomName(a.room) + " · " + Fmt.mb(apk.size) + " · " + Fmt.stars(a.stars) + " ★",
                        10.5.sp, color = if (f) C.HiFg.copy(alpha = 0.58f) else C.Fg3, maxLines = 1,
                    )
                }
                Ico(Icons.ChevRight, 15.dp, if (f) C.HiFg else C.Fg3)
            }
            /* 分隔线与行内容同宽（行有 10dp 内边距），否则线比内容两头各长出一截 */
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .height(1.dp).background(C.Line),
            )
        }
    }
}

private fun okColor() = if (C.dark) Color(0xFF6FCF9B) else Color(0xFF2E8B62)

/** 同房间优先，凑满 3 个推荐 */
private fun moreOf(a: App): List<App> {
    val same = Catalog.visible.filter { it.room == a.room && it.id != a.id }.sortedByDescending { it.stars }
    val rest = Catalog.visible.filter { it.room != a.room && it.id != a.id }.sortedByDescending { it.stars }
    return (same + rest).take(3)
}

private fun daysApart(from: String, to: String): Long? = runCatching {
    java.time.temporal.ChronoUnit.DAYS.between(
        java.time.LocalDate.parse(from.take(10)),
        java.time.LocalDate.parse(to.take(10)),
    )
}.getOrNull()

private fun daysSince(date: String): Int = runCatching {
    java.time.temporal.ChronoUnit.DAYS.between(
        java.time.LocalDate.parse(date.take(10)),
        java.time.LocalDate.now(),
    ).toInt().coerceAtLeast(0)
}.getOrDefault(0)

/** 发布节奏：近几个版本的平均间隔 */
private fun cadence(rels: List<Release>): String {
    if (rels.size < 2) return str(R.string.detail_single)
    val span = daysApart(rels.last().date, rels.first().date) ?: return str(R.string.detail_single)
    val avg = (span / (rels.size - 1)).toInt().coerceAtLeast(1)
    return if (avg < 45) str(R.string.detail_cadence_days, avg)
    else str(R.string.detail_cadence_months, (avg / 30).coerceAtLeast(1))
}

/* ---------------- 设置 ---------------- */
@Composable
private fun BoxScope.SettingsScreen(
    toast: (String) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
    handoff: FocusHandoff,
) {
    val ctx = LocalContext.current
    val tick = Device.tick
    var cacheTick by remember { mutableIntStateOf(0) }
    val cache = remember(cacheTick, tick) { Device.cacheBytes(ctx) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    var sw by remember { mutableStateOf(mapOf("auto" to true, "wifi" to true, "boot" to false, "voice" to true, "motion" to true)) }

    /* 语言选择走弹层：语言表见 Langs，加语言只需往表里追加一行。
       langBack 只负责「关闭后把焦点还回这一行」，首次组合时不该抢焦点。 */
    var pickLang by remember { mutableStateOf(false) }
    var langBack by remember { mutableStateOf(false) }
    val langReq = remember { FocusRequester() }
    LaunchedEffect(pickLang) {
        if (!pickLang && langBack) { delay(80); runCatching { langReq.requestFocus() } }
    }
    if (pickLang) LangPickerDialog(
        current = Prefs.lang,
        onDismiss = { pickLang = false },
        onPick = { i ->
            Prefs.useLang(i)
            toast(str(R.string.toast_lang, Langs.label(i)))
            pickLang = false
        },
    )


    /* 下载源：三档。副标题必须写「当前实际走哪条」——自动档的结果取决于系统语言，
       只写「自动」的话用户不知道现在到底有没有走镜像。点整行循环切换（与外观主题同一套手势）。 */
    val srcLabels = listOf(
        str(R.string.source_auto), str(R.string.source_github), str(R.string.source_mirror),
    )
    Column(Modifier.at(0, 96).width(1152.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetRow(
            title = str(R.string.set_theme),
            sub = str(R.string.set_theme_sub),
            height = 58.dp,
            requester = handoff.target,
            control = {
                Seg(
                    labels = listOf(str(R.string.seg_dark), str(R.string.seg_light)),
                    active = if (C.dark) 0 else 1,
                    fg = if (it) C.HiFg else C.Fg,
                )
            },
            onClick = { Prefs.useTheme(if (C.dark) 1 else 2) },
        )
        SetRow(
            title = str(R.string.set_language),
            sub = str(R.string.set_language_sub),
            height = 58.dp,
            requester = langReq,
            control = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(
                        Langs.label(Prefs.lang), 11.5.sp,
                        color = if (it) C.HiFg else C.Fg2, weight = FontWeight.Medium, maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Chev()
                }
            },
            onClick = { langBack = true; pickLang = true },
        )
        SetRow(
            title = str(R.string.set_mirror),
            sub = when (Prefs.source) {
                Prefs.SOURCE_GITHUB -> str(R.string.set_source_github_sub)
                Prefs.SOURCE_MIRROR -> str(R.string.set_source_mirror_sub)
                else -> str(
                    R.string.set_source_auto_sub,
                    str(if (Mirror.usingMirror()) R.string.src_mirror else R.string.src_direct),
                )
            },
            height = 58.dp,
            control = {
                Seg(labels = srcLabels, active = Prefs.source, fg = if (it) C.HiFg else C.Fg)
            },
            onClick = {
                val next = (Prefs.source + 1) % srcLabels.size
                Prefs.useSource(next)
                toast(str(R.string.toast_source, srcLabels[next]))
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SetRow(
                title = str(R.string.set_auto),
                sub = str(R.string.set_auto_sub),
                modifier = Modifier.weight(1f),
                control = { Switch(sw["auto"] == true, f = it) },
                onClick = { sw = sw + ("auto" to !(sw["auto"] ?: false)) },
            )
            SetRow(
                title = str(R.string.set_wifi),
                sub = str(R.string.set_wifi_sub),
                modifier = Modifier.weight(1f),
                control = { Switch(sw["wifi"] == true, f = it) },
                onClick = { sw = sw + ("wifi" to !(sw["wifi"] ?: false)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SetRow(
                title = str(R.string.set_boot),
                sub = str(R.string.set_boot_sub),
                modifier = Modifier.weight(1f),
                control = { Switch(sw["boot"] == true, f = it) },
                onClick = { sw = sw + ("boot" to !(sw["boot"] ?: false)) },
            )
            SetRow(
                title = str(R.string.set_voice),
                sub = str(R.string.set_voice_sub),
                modifier = Modifier.weight(1f),
                control = { Switch(sw["voice"] == true, f = it) },
                onClick = { sw = sw + ("voice" to !(sw["voice"] ?: false)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SetRow(
                title = str(R.string.set_cache),
                sub = str(R.string.set_cache_sub, Fmt.mb(cache)),
                modifier = Modifier.weight(1f),
                control = { Chev() },
                onClick = { Device.clearCache(ctx); cacheTick++; toast(str(R.string.toast_cleared, Fmt.mb(cache))) },
            )
            /* 自更新：索引里的 self 段就是 OpenAPK TV 自己的最新 Release。
               进度直接读 Installer.jobs（SnapshotStateMap，写入即触发重组），不需要另立状态。 */
            val selfRel = UpdateIndex.self
            val selfHasNew = selfRel != null && UpdateIndex.selfNewer()
            val selfJob = Installer.jobs[Installer.SELF_ID]
            SetRow(
                title = when {
                    selfHasNew -> str(R.string.set_check_new, selfRel.tag)
                    else -> str(R.string.set_check)
                },
                sub = when {
                    selfJob is Job.Downloading -> str(R.string.set_check_dl, (selfJob.fraction * 100).toInt())
                    selfJob == Job.Installing -> str(R.string.set_check_install)
                    selfJob is Job.Failed -> str(R.string.set_check_failed, selfJob.message)
                    selfHasNew -> str(R.string.set_check_new_sub, Fmt.mb(selfRel.apk?.size ?: 0L))
                    else -> str(R.string.set_check_sub, version)
                },
                modifier = Modifier.weight(1f),
                control = { Chev() },
                onClick = {
                    val apk = selfRel?.apk
                    when {
                        selfHasNew && apk != null -> Installer.startSelfUpdate(ctx, apk)
                        selfHasNew -> toast(str(R.string.err_generic))
                        else -> toast(str(R.string.toast_uptodate, version))
                    }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SetRow(
                title = str(R.string.set_motion),
                sub = str(R.string.set_motion_sub),
                modifier = Modifier.weight(1f),
                control = { Switch(sw["motion"] == true, f = it) },
                onClick = { sw = sw + ("motion" to !(sw["motion"] ?: false)) },
            )
            SetRow(
                title = str(R.string.set_about),
                sub = str(R.string.set_about_sub, version),
                modifier = Modifier.weight(1f),
                control = { Chev() },
                onClick = onAbout,
            )
        }
        SetRow(
            title = str(R.string.set_reset),
            sub = null,
            height = 54.dp,
            control = { T(str(R.string.set_reset_sub), 10.5.sp, color = C.Fg3) },
            onClick = {
                Prefs.useTheme(0); Prefs.useLang(0); Prefs.useSource(Prefs.SOURCE_AUTO)
                toast(str(R.string.toast_reset))
            },
        )
    }

    SubHead(
        title = str(R.string.settings_title),
        sub = str(R.string.settings_sub),
        onBack = onBack,
    )
}

/* ---------------- 关于 ---------------- */
/** AOSP TV 固件里的浏览器桩：响应 VIEW https，但打开后只会说「没有浏览器」。 */
private const val TV_BROWSER_STUB = "com.android.tv.frameworkpackagestubs"

/**
 * 独立的关于页（设置 → 关于）。二级页，页头与设置页同一套（左上返回胶囊 + 标题）。
 *
 * 三段纵向结构：品牌 masthead（96..192）→ 2×3 信息格（216..524）→ 许可与致谢小字（556..）。
 * 信息格只放「一句话能说完、且用户会想确认」的东西：目录规模、版本、许可、上游、
 * 仓库地址、设备 —— 太长的叙述交给底部小字，格子里塞满就变成一堵墙。
 *
 * 整页只有「项目仓库」可聚焦：电视上没有剪贴板，给一个入口直接调系统浏览器；
 * 没装浏览器时 Intent 会抛 ActivityNotFoundException，转成 toast，不要静默失败。
 */
@Composable
private fun BoxScope.AboutScreen(toast: (String) -> Unit, onBack: () -> Unit, handoff: FocusHandoff) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val total = remember { Fmt.count(Catalog.apps.size) }
    val cats = remember { Fmt.count(Catalog.apps.map { it.group }.distinct().size) }
    val device = "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} " +
        "(API ${android.os.Build.VERSION.SDK_INT})"

    /* 品牌块：彩色标识必须走 Brand()，Ico() 的 tint 会把壳和笑脸染成同一个颜色 */
    Row(
        Modifier.at(0, 96).width(1152.dp).height(96.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Brand(84.dp)
        Spacer(Modifier.width(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            T(str(R.string.app_name), 32.sp, weight = FontWeight.Bold, tight = true)
            T(str(R.string.about_tagline), 12.5.sp, color = C.Fg3, maxLines = 1)
        }
    }

    Column(Modifier.at(0, 216).width(1152.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InfoCell(
                str(R.string.about_cat), str(R.string.about_cat_value, total, cats),
                Modifier.weight(1f),
            )
            InfoCell(
                str(R.string.about_build), str(R.string.about_build_value, version),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InfoCell(
                str(R.string.about_license), str(R.string.about_license_value),
                Modifier.weight(1f),
            )
            InfoCell(
                str(R.string.about_source), str(R.string.about_source_value),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InfoCell(
                str(R.string.about_repo), str(R.string.about_repo_value),
                Modifier.weight(1f), requester = handoff.target, arrow = true,
            ) {
                /* 先问系统有没有浏览器再 startActivity：电视多半没装，
                   直接 start 的话系统会先弹一句「您没有可执行此操作的应用」，
                   语气生硬且不在我们的文案体系里。宁可自己先判断。
                   Android TV 固件里有个 frameworkpackagestubs.BrowserStub 也会响应
                   VIEW https（它就是个「我没有浏览器」的桩），要把它排除掉，
                   否则判断永远为真、还是落到系统提示。 */
                val view = Intent(Intent.ACTION_VIEW, Uri.parse(str(R.string.about_repo_url)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val can = ctx.packageManager
                    .queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
                    .any { it.activityInfo.packageName != TV_BROWSER_STUB }
                if (!can || runCatching { ctx.startActivity(view) }.isFailure) {
                    toast(str(R.string.about_no_browser))
                }
            }
            InfoCell(str(R.string.about_device), device, Modifier.weight(1f))
        }
    }

    /* 许可与出处：这是 GPL 衍生版必须留在应用内的声明之一，别因为「小字不好看」删掉 */
    Column(Modifier.at(0, 556).width(1152.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(str(R.string.about_help), 10.5.sp, color = C.Fg3, maxLines = 3)
        T(str(R.string.about_credits), 10.5.sp, color = C.Fg3.copy(alpha = 0.78f), maxLines = 3)
    }

    SubHead(
        title = str(R.string.set_about),
        sub = str(R.string.about_build_value, version),
        onBack = onBack,
    )
}

/**
 * 关于页的信息格：小标 + 值。不描边，与全站卡片同一套语言。
 * 只有传了 [onClick] 才包 Focusable —— 纯展示格子不进焦点链，
 * 否则用户按方向键会在几个死格子之间来回跳。
 */
@Composable
private fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    arrow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable (Boolean) -> Unit = { f ->
        Row(
            Modifier.fillMaxWidth().height(92.dp).ring(f, 16.dp, 1.012f)
                .background(if (f) C.HiBg else C.RowBg, shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T(
                    label, 10.5.sp,
                    color = (if (f) C.HiFg else C.Fg3), weight = FontWeight.Medium, maxLines = 1,
                )
                T(
                    value, 14.sp,
                    color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1,
                )
            }
            if (arrow) {
                Spacer(Modifier.width(10.dp))
                T("›", 15.sp, color = if (f) C.HiFg.copy(alpha = 0.6f) else C.Fg3)
            }
        }
    }
    if (onClick == null) Box(modifier) { body(false) }
    else Focusable(onClick, modifier, requester) { f -> body(f) }
}

/**
 * 语言选择弹层。用 Dialog 而不是页面内浮层：它的窗口天然把焦点关在里面，
 * 返回键也由系统转成 onDismissRequest，不用自己写焦点陷阱。
 * 列表是数据驱动的（[Langs]），语言再多也只是多几行，可滚动。
 */
@Composable
private fun LangPickerDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        /* 弹层是独立窗口，设计坐标系不会自动继承，这里按 1280×720 重新算一遍比例 */
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = LocalDensity.current
            val s = min(maxWidth.value / 1280f, maxHeight.value / 720f)
            CompositionLocalProvider(LocalDensity provides Density(d.density * s, 1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().background(Color(0xA6000000)))

                    Column(
                        Modifier.width(560.dp)
                            .cardShadow(RoundedCornerShape(22.dp), 34.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(C.Card)
                            .padding(vertical = 18.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 22.dp)) {
                            T(str(R.string.set_language), 18.sp, weight = FontWeight.Bold, tight = true)
                            Spacer(Modifier.height(4.dp))
                            T(str(R.string.lang_picker_sub, Langs.all.size), 11.sp, color = C.Fg3)
                        }
                        Spacer(Modifier.height(12.dp))

                        val reqs = remember { Langs.all.indices.map { FocusRequester() } }
                        LaunchedEffect(Unit) {
                            delay(90)
                            runCatching { reqs.getOrNull(current)?.requestFocus() }
                        }
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = (7 * 52).dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(Langs.all) { i, _ ->
                                LangRow(
                                    label = Langs.label(i),
                                    checked = i == current,
                                    requester = reqs[i],
                                ) { onPick(i) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 弹层里的一行语言。行内反色示焦（不挂外扩焦点环：LazyColumn 会裁边，环会被切平） */
@Composable
private fun LangRow(
    label: String,
    checked: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    Focusable(onClick, Modifier.fillMaxWidth(), requester = requester) { f ->
        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .background(if (f) C.HiBg else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(
                label, 14.sp,
                color = if (f) C.HiFg else C.Fg,
                weight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (checked) Ico(Icons.Check, 15.dp, if (f) C.HiFg else C.Fg2)
        }
    }
}

@Composable
private fun SetRow(
    title: String,
    sub: String?,
    modifier: Modifier = Modifier,
    height: Dp = 62.dp,
    requester: FocusRequester? = null,
    control: @Composable (focused: Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Focusable(onClick, modifier, requester) { f ->
        Row(
            Modifier.fillMaxWidth().height(height).ring(f, 15.dp, 1.012f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(15.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(title, 13.sp, color = if (f) C.HiFg else C.Fg, weight = FontWeight.SemiBold, maxLines = 1)
                if (sub != null) {
                    T(sub, 10.5.sp, color = (if (f) C.HiFg else C.Fg).copy(alpha = 0.58f), maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
            control(f)
        }
    }
}

/** Android（Material 3）规格的开关：轨道 52×32，滑块关闭 16dp / 开启 24dp，
 *  关闭态 = 2dp 描边 + 灰滑块，开启态 = 实心绿轨道 + 白滑块，尺寸与位移都做 180ms 过渡。
 *  f 是所在行的聚焦态：聚焦时行底翻成 HiBg，关闭态的描边和灰滑块必须跟着翻色，否则会看不见。 */
@Composable
private fun Switch(on: Boolean, f: Boolean = false) {
    val thumb by animateDpAsState(if (on) 24.dp else 16.dp, tween(180), label = "thumb")
    val shift by animateDpAsState(if (on) 24.dp else 8.dp, tween(180), label = "shift")
    val track by animateColorAsState(if (on) C.SwitchOn else Color.Transparent, tween(180), label = "track")
    val edge = if (on) Color.Transparent else if (f) C.HiFg.copy(alpha = 0.45f) else C.Fg3
    val knob = if (on) Color.White else if (f) C.HiFg.copy(alpha = 0.72f) else C.Fg3
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape)
            .background(track).border(2.dp, edge, CircleShape),
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = shift)
                .size(thumb).background(knob, CircleShape),
        )
    }
}

/**
 * 「改用国内镜像？」询问框。触发条件只有一处：[Prefs.noteSystemLanguage] 发现系统语言刚变成
 * 简体中文、而当前并没有在走镜像。
 *
 * 与语言弹层同一套做法：Dialog 自己的窗口把焦点关在里面，BACK 由系统转成 onDismissRequest，
 * 不用手写焦点陷阱；坐标系也是独立窗口，得按 1280×720 重算一遍比例。
 * 默认焦点落在「改为国内镜像」——简中系统 + 直连 GitHub 是唯一会走到这里的组合，推荐项就是它。
 */
@Composable
private fun MirrorAskDialog(onSwitch: () -> Unit, onKeep: () -> Unit) {
    Dialog(onDismissRequest = onKeep, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = LocalDensity.current
            val s = min(maxWidth.value / 1280f, maxHeight.value / 720f)
            CompositionLocalProvider(LocalDensity provides Density(d.density * s, 1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().background(Color(0xA6000000)))

                    Column(
                        Modifier.width(600.dp)
                            .cardShadow(RoundedCornerShape(22.dp), 34.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(C.Card)
                            .padding(horizontal = 26.dp, vertical = 26.dp),
                    ) {
                        T(str(R.string.ask_mirror_title), 20.sp, weight = FontWeight.Bold, tight = true)
                        Spacer(Modifier.height(10.dp))
                        T(str(R.string.ask_mirror_body), 12.5.sp, color = C.Fg2, line = 1.6f)
                        Spacer(Modifier.height(10.dp))
                        T(str(R.string.ask_mirror_note), 11.sp, color = C.Fg3, line = 1.5f)
                        Spacer(Modifier.height(24.dp))

                        val yes = remember { FocusRequester() }
                        val no = remember { FocusRequester() }
                        LaunchedEffect(Unit) { delay(120); runCatching { yes.requestFocus() } }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DarkBtn(
                                str(R.string.ask_mirror_yes), Modifier.weight(1f),
                                requester = yes, dot = false, onClick = onSwitch,
                            )
                            GhostBtn(
                                str(R.string.ask_mirror_no), null,
                                modifier = Modifier.weight(1f), height = 46.dp,
                                requester = no, onClick = onKeep,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* 任务被拦下或失败时的说明弹层：解释「为什么装不上」，并给出可执行的下一步。
   跟 MirrorAskDialog 同构：遥控器方向键可达，默认焦点落在主操作上。 */
@Composable
private fun JobDialog(
    title: String,
    body: String,
    note: String?,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String?,
    onSecondary: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = LocalDensity.current
            val s = min(maxWidth.value / 1280f, maxHeight.value / 720f)
            CompositionLocalProvider(LocalDensity provides Density(d.density * s, 1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().background(Color(0xA6000000)))

                    Column(
                        Modifier.width(620.dp)
                            .cardShadow(RoundedCornerShape(22.dp), 34.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(C.Card)
                            .padding(horizontal = 26.dp, vertical = 26.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).background(Color(0x24E7B24C), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Ico(Icons.Shield, 15.dp, Color(0xFFE7B24C)) }
                            Spacer(Modifier.width(11.dp))
                            T(title, 20.sp, weight = FontWeight.Bold, tight = true)
                        }
                        Spacer(Modifier.height(12.dp))
                        T(body, 12.5.sp, color = C.Fg2, line = 1.65f)
                        if (note != null) {
                            Spacer(Modifier.height(10.dp))
                            T(note, 11.sp, color = C.Fg3, line = 1.55f)
                        }
                        Spacer(Modifier.height(24.dp))

                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { delay(120); runCatching { focus.requestFocus() } }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DarkBtn(
                                primary, Modifier.weight(1f),
                                requester = focus, dot = false, onClick = onPrimary,
                            )
                            if (secondary != null) GhostBtn(
                                secondary, null,
                                modifier = Modifier.weight(1f), height = 46.dp,
                                onClick = { onSecondary?.invoke() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Seg(labels: List<String>, active: Int, fg: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0x29808080)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == active
            Box(
                Modifier.height(24.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color(0x47808080) else Color.Transparent)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(
                    label, 11.sp, color = fg.copy(alpha = if (on) 1f else 0.6f),
                    weight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Chev() {
    T("›", 13.sp, color = C.Fg3)
}

/* ---------------- 通用按钮 ---------------- */
@Composable
private fun DarkBtn(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    progress: Float? = null,
    requester: FocusRequester? = null,
    /** 左侧那个深色圆底座（放图标或进度环）。纯文字按钮（弹窗里的确认键）传 false，否则是个空点 */
    dot: Boolean = true,
    onClick: () -> Unit,
) {
    Focusable(onClick, modifier, requester) { f ->
        Row(
            Modifier.fillMaxWidth().height(46.dp).ring(f, 13.dp, 1.01f)
                .background(if (f) C.BtnBg.copy(alpha = 0.92f) else C.BtnBg, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot) {
                Box(
                    Modifier.size(17.dp).background(if (enabled) C.BtnFg else C.BtnFg.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        progress != null -> Ring(progress, C.BtnBg, C.BtnFg.copy(alpha = 0.3f), 12.dp)
                        icon != null -> Ico(icon, 9.dp, C.BtnBg)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            T(text, 13.sp, color = C.BtnFg.copy(alpha = if (enabled) 1f else 0.5f), weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun GhostBtn(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Focusable(onClick, modifier, requester) { f ->
        Row(
            Modifier.fillMaxWidth().height(height).ring(f, 12.dp, 1.01f)
                .background(if (f) C.HiBg else C.RowBg, RoundedCornerShape(12.dp))
                .border(1.dp, if (f) C.HiBg else C.Line, RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Ico(icon, 14.dp, if (f) C.HiFg else C.Fg2)
                Spacer(Modifier.width(7.dp))
            }
            T(text, 12.sp, color = if (f) C.HiFg else C.Fg2, weight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/* ---------------- 页头 / 提示 / toast ---------------- */
@Composable
private fun BoxScope.ToastHost(text: String?) {
    AnimatedVisibility(
        visible = text != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        Box(
            Modifier.padding(bottom = 14.dp).clip(CircleShape)
                .background(if (C.dark) Color(0xE60C0F15) else Color(0xF2101620))
                .border(1.dp, Color(0x24FFFFFF), CircleShape)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            T(text ?: "", 12.sp, color = Color.White, maxLines = 1)
        }
    }
}

/* ============================================================
   绝对定位助手：面板内坐标（34 / 72 这种原型像素直接写）
   ============================================================ */
private fun Modifier.at(x: Int, y: Int): Modifier =
    this.offset(x = x.dp, y = y.dp)

private fun Modifier.at(x: Int, y: Dp): Modifier =
    this.offset(x = x.dp, y = y)

/* ============================================================
   图片：加载 / 蒙版 / 取色
   ============================================================ */
@Composable
private fun art(url: String?, maxWidth: Int): ImageBitmap? {
    var bmp by remember(url, maxWidth) { mutableStateOf(url?.let { Images.cached(it, maxWidth) }) }
    LaunchedEffect(url, maxWidth) {
        if (bmp == null && url != null) bmp = Images.load(url, maxWidth)
    }
    return bmp
}

@Composable
private fun MaskedArt(bmp: ImageBitmap, maskStart: Float, maskEnd: Float) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            maskStart to Color.Transparent,
                            (maskStart + 0.054f) to Color.Black,
                            (maskEnd - 0.054f) to Color.Black,
                            maskEnd to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
    }
}

/**
 * 无图标卡片里应用名的字号。
 *
 * 原来写的是 `宽 × 0.068`，在 177×100 的海报卡上只有 12sp，名字缩成一个小点。
 * 这里改成「从大到小试，直到确实放得下」：按卡片内宽估算单行能排几个字符，
 * 算出会折几行，再校验总行高没超过卡片。短名（Piik、Home）能撑满卡片，
 * 长名（Kodi HDR Edition）自动缩小折两行，两端都不溢出。
 *
 * 字符宽度用平均值估算：Geist 粗体约 0.60em，CJK 按 2 个拉丁字符宽计。
 * 另外单独校验「最长的那个单词」——它不能被折断，否则会吃省略号。
 */
private fun nameFontSize(name: String, w: Float, h: Float, maxLines: Int): Float {
    val n = name.length.coerceAtLeast(1)
    val cjk = name.count { it.code >= 0x2E80 }
    val em = n - cjk + cjk * 2f
    val longest = name.split(' ').maxOfOrNull { word ->
        val c = word.count { it.code >= 0x2E80 }
        (word.length - c + c * 2).toFloat()
    } ?: em
    val availW = w * 0.84f
    val availH = h * 0.78f
    var fs = minOf(w * 0.20f, h * 0.46f, 36f)
    while (fs > 8f) {
        val perLine = (availW / (fs * 0.60f)).coerceAtLeast(1f)
        val lines = kotlin.math.ceil(em / perLine).toInt().coerceAtLeast(1)
        if (lines <= maxLines && longest <= perLine && lines * fs * 1.26f <= availH) return fs
        fs -= 0.5f
    }
    return 8f
}

/**
 * 圆角半径按卡片短边取比例：参考图的圆角约等于短边的 25%，上限 28dp
 * 免得太大的横幅卡被圆成一粒胶囊。
 *
 * 聚焦环必须用同一份半径（`tileRadius`），否则环还是小圆角、卡是大圆角，两者对不上。
 */
private fun tileRadius(shortSide: Dp): Dp = (shortSide * 0.25f).coerceIn(8.dp, 28.dp)

private fun tileShape(shortSide: Dp): RoundedCornerShape = RoundedCornerShape(tileRadius(shortSide))

/**
 * 没有横幅时的兜底卡：从应用图标里取主色相，整块铺成同色系的柔和渐变底，
 * 让每张「只有图标」的卡片自带一种颜色，而不是统一的灰白块。
 */
@Composable
private fun NameCard(app: App) {
    val url = app.art.icon
    val raw = art(url, 192)
    /* 三种状态必须分清：还在下 / 确实没有图 / 已就绪。
       只按 raw == null 判断会把「加载中」当成「没有图」，于是先闪一版文字再跳成图标 */
    val absent = remember(url, Images.epoch) { url == null || Images.knownBad(url) }
    val icon = remember(raw) { raw?.let { trimIcon(it) } }
    val hue = remember(icon, app.id) { iconHue(icon, app.id) }
    val dark = C.dark
    /* 图到位时淡入，避免硬切 */
    val appear by animateFloatAsState(if (icon != null) 1f else 0f, tween(180), label = "iconIn")

    /* 深色：低明度深色块；浅色：高明度淡彩。两端拉开一点，渐变才看得见 */
    val top = hsv(hue, if (dark) 0.50f else 0.30f, if (dark) 0.38f else 1f)
    val bottom = hsv(hue, if (dark) 0.40f else 0.15f, if (dark) 0.20f else 1f)
    /* 图标背后的柔光，避免大面积纯色看起来发平 */
    val glow = hsv(hue, if (dark) 0.72f else 0.60f, if (dark) 0.62f else 1f)
        .copy(alpha = if (dark) 0.26f else 0.26f)

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        /* 名字由外层布局负责（卡片下方、右侧或页面标题），这里只放图标 */
        val side = minOf(maxWidth * 0.40f, maxHeight * 0.58f).coerceIn(28.dp, 110.dp)

        when {
            icon != null -> Box(
                Modifier.size(side).drawBehind {
                    /* 柔光画出边界，但不占布局高度；跟着图标一起淡入 */
                    val r = size.minDimension * 0.92f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = glow.alpha * appear), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                    )
                },
                contentAlignment = Alignment.Center,
            ) { FitIcon(icon, side, fill = 0.94f, modifier = Modifier.graphicsLayer { alpha = appear }) }

            absent -> {
                /* 确实没有图标：直接把应用名当主视觉排进去。
                   字号按可用宽高与名字长度算出来，小缩略图和大海报卡都不会缩小成一点 */
                val lines = if (maxHeight < 48.dp) 2 else 3
                T(
                    app.name,
                    nameFontSize(app.name, maxWidth.value, maxHeight.value, lines).sp,
                    modifier = Modifier.fillMaxWidth(0.86f),
                    color = if (dark) Color.White.copy(alpha = 0.94f) else C.Fg,
                    weight = FontWeight.Bold,
                    maxLines = lines,
                    tight = true,
                    align = TextAlign.Center,
                )
            }

            /* 还在下载：只留渐变底，什么都不画，等图来了淡入 */
        }
    }
}

/**
 * 图标主色相：按「饱和度 × 明度」加权做圆周平均，
 * 白底/透明边与近灰的描边因权重低会被自然忽略。
 * 没有图标、或图标本身就是灰阶时，用 id 散列从一组低饱和色相里挑一个，
 * 保证同一屏里不会出现一堆一模一样的蓝色块。
 */
private fun iconHue(b: ImageBitmap?, seed: String): Float {
    val fallback = ICON_FALLBACK_HUES[Math.floorMod(seed.hashCode(), ICON_FALLBACK_HUES.size)]
    if (b == null) return fallback
    return runCatching {
        val n = 24
        val bm = Bitmap.createScaledBitmap(b.asAndroidBitmap(), n, n, true)
        val hsvArr = FloatArray(3)
        var hx = 0f
        var hy = 0f
        var w = 0f
        for (x in 0 until n) {
            for (y in 0 until n) {
                val p = bm.getPixel(x, y)
                if (((p ushr 24) and 0xFF) < 40) continue
                android.graphics.Color.colorToHSV(p, hsvArr)
                val s = hsvArr[1]
                val v = hsvArr[2]
                if (s < 0.20f || v < 0.14f) continue
                val k = s * v
                val rad = Math.toRadians(hsvArr[0].toDouble())
                hx += (Math.cos(rad) * k).toFloat()
                hy += (Math.sin(rad) * k).toFloat()
                w += k
            }
        }
        if (w < 0.6f) return@runCatching fallback
        var deg = Math.toDegrees(Math.atan2(hy.toDouble(), hx.toDouble())).toFloat()
        if (deg < 0f) deg += 360f
        deg
    }.getOrDefault(fallback)
}

/** 图标取不到颜色时的候选色相：蓝、紫、青、绿、橙、玫红…避开荧光黄绿 */
private val ICON_FALLBACK_HUES = listOf(212f, 266f, 188f, 152f, 28f, 344f, 300f, 240f)

private fun hsv(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

@Composable
fun AppIcon(app: App, size: Dp, modifier: Modifier = Modifier) {
    val url = app.art.icon
    val bmp = art(url, 192)
    /* 确实没有图标（没填 / 下挂了）就整块留空：行内右侧已有应用名 */
    val absent = remember(url, Images.epoch) { url == null || Images.knownBad(url) }
    if (absent) {
        Box(modifier.size(size))
        return
    }
    val appear by animateFloatAsState(if (bmp != null) 1f else 0f, tween(180), label = "appIconIn")
    Box(
        modifier.size(size).clip(tileShape(size))
            .background(C.Fg.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        /* 裁留白 + 按面积归一，行与行的图标视觉尺寸才一致 */
        if (bmp != null) {
            FitIcon(bmp, size, fill = 0.86f, modifier = Modifier.graphicsLayer { alpha = appear })
        }
    }
}

/**
 * 图标视觉归一化。
 *
 * 各个应用图标的源图留白差别极大：有的把图形画满 192×192，有的四周留了 20% 透明边
 * （自适应图标把前景缩在中间），有的干脆是白底中间一个小图形。
 * 直接 Fit 摆进同一个方框，就会「有的大有的小」。
 *
 * 这里先按外圈底色把多余的边裁掉（透明边 / 纯色底都认），再交给调用方 Fit 到统一方框，
 * 视觉重量就一致了。裁得太少（本来就是满幅）或裁得太狠（多半是误判）时保持原图。
 */
private val ICON_TRIM = android.util.LruCache<String, ImageBitmap>(120)

private fun trimIcon(b: ImageBitmap): ImageBitmap {
    val key = System.identityHashCode(b).toString() + "-" + b.width + "x" + b.height
    ICON_TRIM.get(key)?.let { return it }
    val out = runCatching { trimIconOnce(b) }.getOrDefault(b)
    ICON_TRIM.put(key, out)
    return out
}

private fun trimIconOnce(b: ImageBitmap): ImageBitmap {
    val bm = b.asAndroidBitmap()
    val w = bm.width
    val h = bm.height
    if (w < 8 || h < 8) return b
    val px = IntArray(w * h)
    bm.getPixels(px, 0, w, 0, 0, w, h)

    /* 外圈 5% 的底色：统计平均色，同时算透明的比例 */
    val band = (minOf(w, h) / 20).coerceAtLeast(1)
    var sr = 0L
    var sg = 0L
    var sb = 0L
    var opaque = 0
    var edgeCount = 0
    for (y in 0 until h) {
        val rowEdge = y < band || y >= h - band
        for (x in 0 until w) {
            if (!rowEdge && x >= band && x < w - band) continue
            edgeCount++
            val p = px[y * w + x]
            if (((p ushr 24) and 0xFF) < 24) continue
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
            opaque++
        }
    }
    if (edgeCount == 0) return b
    /* 外圈一半以上透明 → 按「透明边」处理，不比颜色。
       注意外圈可能一个不透明像素都没有（自适应图标把前景缩在中间），这时也要能裁 */
    val clearEdge = opaque * 2 < edgeCount
    val cr = if (opaque > 0) (sr / opaque).toInt() else 255
    val cg = if (opaque > 0) (sg / opaque).toInt() else 255
    val cb = if (opaque > 0) (sb / opaque).toInt() else 255

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    /* 包围盒按 step 抽样就够：裁的是留白，±2px 的误差看不出来，
       但 480 宽的横幅从 13 万次判读降到 3 万次，主线程不用卡住 */
    val step = if (maxOf(w, h) > 160) 2 else 1
    for (y in 0 until h step step) {
        val row = y * w
        for (x in 0 until w step step) {
            val p = px[row + x]
            val a = (p ushr 24) and 0xFF
            val content = if (clearEdge) {
                a >= 40
            } else {
                val dr = ((p shr 16) and 0xFF) - cr
                val dg = ((p shr 8) and 0xFF) - cg
                val db = (p and 0xFF) - cb
                a < 200 || dr * dr + dg * dg + db * db > 900
            }
            if (!content) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < minX || maxY < minY) return b
    /* 抽样会漏掉最外一圈，补回一个步长再夹进原图 */
    minX = (minX - step).coerceAtLeast(0)
    minY = (minY - step).coerceAtLeast(0)
    maxX = (maxX + step).coerceAtMost(w - 1)
    maxY = (maxY + step).coerceAtMost(h - 1)
    val bw = maxX - minX + 1
    val bh = maxY - minY + 1
    val saved = maxOf((w - bw).toFloat() / w, (h - bh).toFloat() / h)
    /* 削掉不到 6%：本来就画满了，别动 */
    if (saved < 0.06f) return b
    /* 只剩不到两成：多半把内圈内容当成留白裁掉了，保原图 */
    if (bw < w * 0.2f || bh < h * 0.2f) return b
    return Bitmap.createBitmap(bm, minX, minY, bw, bh).asImageBitmap()
}

/**
 * 纯色占位图（SkyTube 的 banner_default.png 就是一张 #222222 的纯色图）。
 * 这种图当横幅用只会得到一块死黑/死白的卡片，按「没有横幅」处理，退回图标卡。
 */
private val ART_BLANK = android.util.LruCache<String, Boolean>(120)

private fun blankArt(b: ImageBitmap?): Boolean {
    if (b == null) return true
    val key = System.identityHashCode(b).toString() + "-" + b.width + "x" + b.height
    ART_BLANK.get(key)?.let { return it }
    val blank = runCatching {
        val n = 8
        val bm = Bitmap.createScaledBitmap(b.asAndroidBitmap(), n, n, true)
        val px = IntArray(n * n)
        bm.getPixels(px, 0, n, 0, 0, n, n)
        var lo = 255
        var hi = 0
        for (p in px) {
            val l = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            if (l < lo) lo = l
            if (l > hi) hi = l
        }
        hi - lo < 8
    }.getOrDefault(false)
    ART_BLANK.put(key, blank)
    return blank
}

/**
 * 把图标按「视觉面积」摆进一个方框，而不是简单的 Fit。
 *
 * 单纯的 Fit 是「长边贴合」：方形图标占满 90% 方框，而 2:1 的横版图标高度只剩一半，
 * 一排看过去就像有的图标大、有的图标小。这里按面积等比换算，
 * 让宽扁的图标横向铺开补足面积（仍严格夹在方框内），视觉重量就一致了。
 */
@Composable
private fun FitIcon(bmp: ImageBitmap, box: Dp, fill: Float = 0.94f, modifier: Modifier = Modifier) {
    val t = remember(bmp) { trimIcon(bmp) }
    val a = t.width.toFloat() / t.height.coerceAtLeast(1)
    val lim = box * fill
    /* 面积 = lim² 的等面积矩形，再夹进方框 */
    var hh = lim * Math.sqrt(1.0 / a).toFloat()
    var ww = hh * a
    if (ww > lim) {
        ww = lim
        hh = ww / a
    }
    if (hh > lim) {
        hh = lim
        ww = hh * a
    }
    Image(
        t, null, modifier.size(ww, hh),
        contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
    )
}

private fun meanColor(b: ImageBitmap): Color = runCatching {
    val px = Bitmap.createScaledBitmap(b.asAndroidBitmap(), 1, 1, true).getPixel(0, 0)
    Color(px).copy(alpha = 1f)
}.getOrDefault(Color(0xFF7C8391))

/** 左右各取 1/22 宽的平均色，给 contain 之后的空白铺底 */
private fun edgeColors(b: ImageBitmap): Pair<Color, Color> = runCatching {
    val bm = b.asAndroidBitmap()
    val w = bm.width
    val h = bm.height
    if (w < 8 || h < 8) return@runCatching Color(0xFF12151C) to Color(0xFF12151C)
    val band = (w / 22).coerceAtLeast(1)
    fun avg(x0: Int, x1: Int): Color {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        var y = 0
        while (y < h) {
            var x = x0
            while (x < x1) {
                val p = bm.getPixel(x, y)
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
                x += 3
            }
            y += 3
        }
        return if (n == 0) Color(0xFF12151C) else Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }
    avg(0, band) to avg(w - band, w)
}.getOrDefault(Color(0xFF12151C) to Color(0xFF12151C))

@Composable
private fun wallTints(hero: List<App>): List<Color> {
    val samples = hero.take(3).map { art(it.art.banner, 24) }
    return remember(samples) {
        samples.mapIndexedNotNull { i, bmp ->
            val base = bmp?.let(::meanColor) ?: return@mapIndexedNotNull null
            vivid(base, FALLBACK_HUES.getOrElse(i) { 210f })
        }
    }
}

private val FALLBACK_HUES = listOf(6f, 174f, 226f)

/** 白底 banner 取出来的平均色几乎是灰，按色相提纯，保证背景有颜色而不是一片炭黑 */
private fun vivid(base: Color, fallbackHue: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (base.red * 255).toInt().coerceIn(0, 255),
            (base.green * 255).toInt().coerceIn(0, 255),
            (base.blue * 255).toInt().coerceIn(0, 255),
        ),
        hsv,
    )
    val hue = if (hsv[1] < 0.18f) fallbackHue else hsv[0]
    val sat = hsv[1].coerceIn(0.48f, 0.78f).coerceAtLeast(0.48f)
    val value = hsv[2].coerceIn(0.34f, 0.58f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
}

/* ============================================================
   安装状态
   ============================================================ */
@Composable
private fun installLabel(app: App, installed: Installed?, job: Job?): String = when {
    job is Job.Held -> str(R.string.action_held)
    job is Job.Downloading -> "${(job.fraction * 100).toInt()}%"
    job == Job.Checking -> str(R.string.job_checking)
    job == Job.Installing -> str(R.string.job_installing)
    /* 装了旧版、或者索引里有更新的 Release，都算「可更新」 */
    installed != null && (installed.versionCode < app.versionCode || UpdateIndex.isNewer(app)) -> str(R.string.action_update)
    installed != null -> str(R.string.action_open)
    else -> str(R.string.hero_install)
}

private fun installOrOpen(ctx: Context, app: App) {
    val inst = Device.installedAs(ctx, app)
    val newer = UpdateIndex.isNewer(app)
    if (inst != null && !newer && inst.versionCode >= app.versionCode) launch(ctx, app.pkg)
    else Installer.start(ctx, app)
}

private data class LocalApp(val pkg: String, val label: String, val version: String?, val icon: ImageBitmap?)

private fun otherApps(ctx: Context, exclude: Set<String>): List<LocalApp> {
    val pm = ctx.packageManager
    return pm.getInstalledApplications(0)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName && it.packageName !in exclude }
        .filter { pm.getLeanbackLaunchIntentForPackage(it.packageName) != null || pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { ai ->
            val icon = runCatching {
                val d = pm.getApplicationIcon(ai)
                val bm = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 96, 96)
                d.draw(android.graphics.Canvas(bm))
                bm.asImageBitmap()
            }.getOrNull()
            LocalApp(
                ai.packageName, pm.getApplicationLabel(ai).toString(),
                runCatching { pm.getPackageInfo(ai.packageName, 0).versionName }.getOrNull(), icon,
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun recentTimes() = strArray(R.array.recent_times)
