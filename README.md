# OpenAPK TV

**English** · [简体中文](README.zh-CN.md)

> **Derivative work.** This repository is an unofficial derivative of
> [Open Shelf](https://github.com/wingedpanther/openshelf) by wingedpanther: full Simplified Chinese
> localisation, China-friendly download mirrors, an independent brand. First modified 2026-11-14,
> released under GPL-3.0. Changes, copyright and third-party asset notices are in
> [NOTICE.md](NOTICE.md). Not affiliated with, endorsed by or supported by the upstream project.

An app store for Android TV that lists open-source TV apps from GitHub and installs them from each project's releases, using only the remote.

Google Play on TV only carries apps that publish there. Plenty of good TV apps only live on GitHub, where installing one means a phone, a download link and a guess at which APK fits your box. OpenAPK TV does that part for you.

## Download

Latest: [**v1.0.4**](https://github.com/harrisonxhere/openapk-tv/releases/latest) — 3.2 MB, Android 9 (API 28) or newer, armeabi-v7a / arm64-v8a / x86.

```bash
adb install -r openapk-tv-1.0.4.apk
```

The package is not marked `testOnly`, so a USB stick or a file manager on the TV installs it as is. Every release page carries the SHA256.

Coming from 1.0.1 or earlier? Uninstall the old version first — those builds were signed with a throwaway CI debug key, so Android refuses the update with a signature mismatch. From 1.0.2 on, releases share one fixed signing key and overwrite each other normally.

## What it does

- Lists about 2,900 Android TV apps from GitHub across 18 categories
- Every app in the list declares TV support in its manifest and ships an APK in its latest release
- Search by name or by what an app does, like "video player" or "launcher"
- Picks the APK that matches your TV's CPU
- Checks the signing key before installing an update, and holds the update if the key changed
- Downloads straight from GitHub, or through a mirror when GitHub is slow or blocked — chosen automatically from your system language, overridable in Settings
- My Apps shows what's installed, with updates for apps in the catalog
- Light and dark themes, and a choice of accent colours
- English and Simplified Chinese, following the system or picked by hand
- IPTV players and debrid or Stremio streaming clients are hidden by default, with a switch in Settings to show them
- When an app looks abandoned, its page points to a maintained fork if one ships a newer TV build

## Screenshots

| | |
|---|---|
| ![Discover, dark](docs/screenshots/current/home-dark.png) | ![Discover, light](docs/screenshots/current/home-light.png) |
| Discover (dark) | Discover (light) |

![Settings](docs/screenshots/current/settings-dark.png)

## Install

You need an Android TV, Google TV, Fire TV or other Android box running Android 9 or newer.

1. Download the latest `openapk-tv-x.y.z.apk` from this repository's Releases.
2. Sideload it with an app like Downloader, or from a computer with `adb install openapk-tv-x.y.z.apk`.
3. The first time you install an app, Android asks you to allow installs from OpenAPK TV. Allow it once.

## The catalog

The app list is built from public GitHub data and ships inside the APK. An app is included when:

- the repository isn't a fork, unless the original is archived or hasn't had a release in a year, and the fork has at least 10 stars and ships a newer TV build
- its latest release has an APK, published in the last 3 years
- that APK's manifest has the Android TV launcher entry

Apps with fewer than 10 stars stay out of browsing, but search still finds them by their exact name.

Updates are tracked separately: a scheduled workflow asks GitHub for each project's latest release and publishes a small index, so the app can badge new versions without downloading anything.

## Project layout

Code, artwork and decision records all live in this repository; a clone is the whole thing.

| Path | Contents |
|---|---|
| `app/src/main/java/app/tvhub/` | All Kotlin source (7 files, ~5,700 lines): `Screens.kt` for the views, `Ui.kt` for the design system, `Data.kt` for data and preferences, `Installer.kt` for download and install, `Mirror.kt` for download sources, `UpdateIndex.kt` for version checking |
| `app/src/main/res/values{,-zh-rCN}/` | English base strings plus Simplified Chinese (300+ keys each) |
| `docs/screenshots/current/` | Screenshots of the current build, both themes and Settings |
| `art/` | Brand sources: `logo.svg` (cat-eared TV with a robot face on screen) and the icon/banner generator |

## Documentation

- [`AGENTS.md`](AGENTS.md) — project handbook: hard constraints, code map, build commands, known traps. **Start here if you're changing code with an AI tool.**
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — how data flows, how installing works, how localisation is wired
- [`docs/DESIGN-SYSTEM.md`](docs/DESIGN-SYSTEM.md) — how 1280×720 design coordinates become dp, colour tokens, focus rings, image feathering

Also: `docs/SCREENS.md` (coordinates per view), `docs/BUILD-AND-TEST.md` (build, emulator, device, screenshots), `docs/DECISIONS.md` (why it looks this way, including six rejected designs), `docs/INTRO.md` (long-form introduction, in Chinese).

## Build

Needs JDK 17 or newer and the Android SDK. Point `local.properties` at the SDK (`sdk.dir=...`), then:

```
./gradlew assembleRelease
```

Release builds are signed with the debug key unless a `keystore.properties` file sits in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Translations

All the app's text is in [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml). To add a language, copy that file to `values-<code>/strings.xml` (for example `values-de` for German), translate the text, add one line to the `Langs` table in `Data.kt`, and open a pull request.

- Keep placeholders like `%1$s` and `%1$d` as they are. They're filled in with names and numbers.
- `fmt_date`, `fmt_short_date` and `fmt_month_year` are date patterns. German would use `d. MMM yyyy`.
- App descriptions come from each project's GitHub page and stay in English.

This README follows the same idea: `README.md` is the English original, `README.zh-CN.md` the Chinese one. A new language gets its own `README.<code>.md`, linked from the line at the top of both.

## Licence

OpenAPK TV is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

The Geist font is licensed under the SIL Open Font License 1.1. See [FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt).

App names, descriptions and artwork in the catalog belong to their projects.
