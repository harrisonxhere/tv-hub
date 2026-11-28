# OpenAPK TV

[English](README.md) · **简体中文**

> **衍生版本。** 本仓库是 [Open Shelf](https://github.com/wingedpanther/openshelf)（作者 wingedpanther）的
> **非官方衍生版**：全量简体中文化、国内下载源、独立品牌。首次修改于 2026-11-14，依 GPL-3.0 开源。
> 修改清单、版权与第三方资产声明见 [NOTICE.md](NOTICE.md)。本项目与上游无隶属、授权或背书关系。

一个给 Android TV 用的**开源应用商店**：把 GitHub 上那些只发 Release、不上架商店的电视应用整理成目录，在电视上直接装。

Google Play 上的电视应用，只有愿意上架的那些。很多好用的电视应用只活在 GitHub 上，装一次要拿手机翻 Releases、下载、再猜哪个 APK 对得上你的盒子。OpenAPK TV 把这段代劳了。

## 下载

最新版本：[**v1.0.4**](https://github.com/harrisonxhere/openapk-tv/releases/latest) — 3.2 MB，最低 Android 9（API 28），支持 armeabi-v7a / arm64-v8a / x86。

```bash
adb install -r openapk-tv-1.0.4.apk
```

包里不带 `testOnly`，电视上用 U 盘或文件管理器直接装即可。每个 Release 页都附 SHA256 供校验。

从 1.0.1 或更早版本升级的，请先卸载旧版：那几版是用 CI 的临时 debug 密钥签的，系统会以签名不符拒绝覆盖安装。1.0.2 起所有版本共用同一把正式密钥，可以正常互相覆盖。

## 它做什么

- 目录收录约 2,900 款 GitHub 上的 Android TV 应用，分 18 个分类
- 每个应用都在清单里声明了 TV 支持，且最新 Release 里带 APK
- 按名字搜，也按用途搜，比如「播放器」「桌面」
- 自动挑与电视 CPU 匹配的那个 APK
- 升级前比对签名，证书变了就拦下，不静默覆盖
- 下载可直连 GitHub，也可走国内镜像 —— 按系统语言自动选，设置里能改
- 「我的应用」列出已装的，目录内的应用有新版本会标出来
- 深浅双主题，强调色可选
- 英文与简体中文，跟随系统或手动切换
- IPTV 播放器、debrid / Stremio 类串流客户端默认隐藏，设置里有开关
- 应用看起来已经弃更时，详情页会指向仍在维护、且带更新 TV 构建的 fork

## 界面

| | |
|---|---|
| ![首页，深色](docs/screenshots/current/home-dark.png) | ![首页，浅色](docs/screenshots/current/home-light.png) |
| 首页（深色） | 首页（浅色） |

![设置](docs/screenshots/current/settings-dark.png)

## 安装

需要 Android 9 以上的 Android TV、Google TV、Fire TV 或其它电视盒子。

1. 在本仓库 Releases 里下载最新的 `openapk-tv-x.y.z.apk`。
2. 用 Downloader 之类的应用传到电视，或在电脑上 `adb install openapk-tv-x.y.z.apk`。
3. 第一次装应用时，系统会问是否允许 OpenAPK TV 安装应用，允许一次即可。

## 目录怎么来的

应用列表由公开的 GitHub 数据构建，随 APK 打包。收录条件是：

- 仓库不是 fork。除非原项目已归档或一年没发版，而 fork 有 10 星以上且 TV 构建更新
- 最新 Release 里有 APK，且是近 3 年内发布的
- 该 APK 的清单里有 Android TV 启动器入口

星数少于 10 的应用不进浏览，但用完整名字搜索仍然搜得到。

版本检测另走一条线：定时任务去 GitHub 拉各项目的最新 Release，产出一份很小的索引，客户端据此给新版本打角标，不用下载任何 APK。

## 项目结构

代码、素材与决策记录都在这个仓库里，克隆下来就是完整的。

| 位置 | 内容 |
|---|---|
| `app/src/main/java/app/tvhub/` | 全部 Kotlin 源码（7 个文件，约 5,700 行）：`Screens.kt` 各视图、`Ui.kt` 设计系统、`Data.kt` 数据与偏好、`Installer.kt` 下载安装、`Mirror.kt` 下载源、`UpdateIndex.kt` 版本检测 |
| `app/src/main/res/values{,-zh-rCN}/` | 英文基准 + 简体中文文案（各 300+ 条） |
| `docs/screenshots/current/` | 当前界面实拍（深浅主题、设置页） |
| `art/` | 品牌真源：`logo.svg`（猫耳电视 + 屏幕内机器人头）与图标/横幅生成脚本 |

## 文档

- [`AGENTS.md`](AGENTS.md) — 项目说明书：硬性约束、代码地图、构建命令、已知的坑。**用 AI 工具改代码从这里开始。**
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — 数据怎么流、安装怎么走、本地化怎么实现
- [`docs/DESIGN-SYSTEM.md`](docs/DESIGN-SYSTEM.md) — 1280×720 设计坐标怎么变成 dp、颜色令牌、焦点环、图片羽化

其余：`docs/SCREENS.md`（每个视图的坐标）、`docs/BUILD-AND-TEST.md`（编译 / 模拟器 / 真机 / 截图）、
`docs/DECISIONS.md`（为什么长这样，含被否定的六版设计）、`docs/INTRO.md`（更长的中文介绍）。

## 构建

需要 JDK 17 以上和 Android SDK。在 `local.properties` 里指向 SDK（`sdk.dir=...`），然后：

```
./gradlew assembleRelease
```

release 包默认用 debug 密钥签名；如果项目根目录放了 `keystore.properties`（含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`），就改用里面的正式密钥。

## 翻译

应用内文案全在 [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml)。加一门语言：把这个文件复制到 `values-<code>/strings.xml`（例如德语是 `values-de`），翻译，再在 `Data.kt` 的 `Langs` 表里加一行，然后提 PR。

- `%1$s`、`%1$d` 这类占位符原样保留，它们会被换成名字和数字。
- `fmt_date`、`fmt_short_date`、`fmt_month_year` 是日期格式，德语用 `d. MMM yyyy`。
- 应用描述来自各项目的 GitHub 页面，保持英文。

这份 README 同理：`README.md` 是英文原文，`README.zh-CN.md` 是中文版。新增语言时另建 `README.<code>.md`，并在两份文件顶部那行语言列表里互相挂上链接。

## 许可

OpenAPK TV 依 GNU General Public License v3.0 发布，见 [LICENSE](LICENSE)。

Geist 字体依 SIL Open Font License 1.1 发布，见 [FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)。

目录内应用的名称、描述与图片归各自项目所有。
