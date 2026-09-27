# HushMessenger v0.0.1

![Version](https://img.shields.io/badge/version-0.0.1-0084ff) ![License](https://img.shields.io/badge/license-GPL--3.0-blue) ![Platform](https://img.shields.io/badge/platform-Android%209%2B%20arm64-green)

HushMessenger is a local source project for Messenger patches compatible with Morphe. It starts with one patch that lets a re-signed Messenger install beside other Meta apps without deleting its shared permission guards. The project builds a patch bundle. You supply your own stock Messenger APK to the patcher.

## Current target

| Field | Value |
| --- | --- |
| Package | `com.facebook.orca` |
| Version | `580.0.0.49.91` |
| Version code | `346013387` |
| Architecture | arm64-v8a |
| Minimum Android version | Android 9 (API 28) |
| Stock APK SHA-256 used for local verification | `128ec75e836f24328d2b28777091c03b20abba0adc536e7ee911ee5fe52e70bc` |

Meta publishes builds that share a version name but differ in code and resources. This first patch declares one exact arm64 target. Read [RESEARCH.md](RESEARCH.md) for the project comparison and [ROADMAP.md](ROADMAP.md) for the next patches.

## Build

Install JDK 21 or newer and use the included Gradle wrapper. The Morphe Gradle plugin resolves a package from GitHub Packages, so set `GITHUB_ACTOR` and `GITHUB_TOKEN` in your environment first. An authenticated GitHub CLI can supply the token with `gh auth token`.

From this folder, run `gradlew.bat :patches:buildAndroid` on Windows. The output is `patches/build/libs/patches-0.0.1.mpp`. Patch only a copy of a stock APK with Morphe Desktop 1.17.0 or a compatible Manager. Keep your signing keystore backed up if you want to update the patched app later.

The bundle contains **Install beside Meta apps**. It renames `com.facebook.permission.prod.FB_APP_COMMUNICATION` and `com.facebook.receiver.permission.ACCESS` to the `app.hushfacebook.*` names used by Hushfacebook. It changes declarations, requests, component guards and matching DEX string loads. Patched Hushfacebook and patched Messenger must be signed with the same key to share those renamed signature permissions. Trust checks and cross-app login still need a signed device test.

The patch bundle built and applied to a copy of the S22's installed Messenger 580 APK on 2026-09-26. The rebuilt manifest had 26 mentions of the first renamed permission and three of the second, with no old-name mentions. Disassembly of the modified DEX found six renamed permission loads and no active old-name loads in those classes. A phone installation and chat-flow check have not been run. The S22's installed app was left untouched.

## Source and license

The build structure comes from the [Morphe patches template](https://github.com/MorpheApp/morphe-patches-template). The permission naming and manifest approach are adapted from [Hushfacebook's shared-permission patch](https://github.com/SysAdminDoc/Hushfacebook/blob/15b8e9ed9315464a3e2d1a821b4e26ad47bbc28c/patches/src/main/kotlin/app/morphe/patches/facebook/coexist/SharedPermissions.kt). Source is licensed under [GPL-3.0](LICENSE); see [NOTICE](NOTICE). HushMessenger is independent of Meta and Morphe.
