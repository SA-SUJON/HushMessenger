![HushMessenger. Keep the conversation. Cut the friction.](assets/readme-hero.png)

<p align="center">
  <a href="https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.2"><img src="https://img.shields.io/badge/version-0.0.2-0084FF" alt="Version 0.0.2"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License GPL-3.0"></a>
  <img src="https://img.shields.io/badge/platform-Android%209%2B%20arm64-3DDC84" alt="Platform Android 9 or newer, arm64">
  <img src="https://img.shields.io/badge/Messenger-580.0.0.49.91-0084FF" alt="Messenger 580.0.0.49.91">
  <img src="https://img.shields.io/badge/status-preview-8A2BE2" alt="Preview release">
</p>

<p align="center">
  <a href="https://ko-fi.com/X8K126YVER"><img height="42" src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Buy me a coffee on Ko-fi"></a>
</p>

# HushMessenger

HushMessenger is a Morphe patch source for Facebook Messenger. Its first patch changes two shared permissions needed for a re-signed Messenger to live beside other Meta apps without dropping the permission guards on its components. You supply your own stock Messenger APK. This repository ships patch code and a patch bundle, not Messenger itself.

[Add the preview source to Morphe](https://morphe.software/add-source?github=SysAdminDoc%2FHushMessenger) | [Download the patch bundle](https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.2) | [See what's next](ROADMAP.md)

> [!IMPORTANT]
> This is an early preview with one patch. The bundle built and applied to a stock APK copy. Its signed output passed APK signature verification, but it has not been installed on a phone. Login, calls, notifications and Facebook sign-on still need a device check.

## What it changes

| Patch | What it does |
| --- | --- |
| `Install beside Meta apps` | Renames Messenger's two shared Meta signature permissions. It changes declarations, requests, component guards and six matching DEX string loads together. The patch stops if the supported APK's manifest or active permission loads differ from the checked layout. |

The new names use the `app.hushfacebook.*` prefix from [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook). If you patch both apps, sign both with the **same key**. Android grants these signature permissions only to apps signed alike. The patch does not yet address every cross-app trust check, so Facebook login and account switching are still on the [roadmap](ROADMAP.md).

## Supported Messenger build

| Field | Value |
| --- | --- |
| Package | `com.facebook.orca` |
| Version | `580.0.0.49.91` |
| Version code | `346013387` |
| Architecture | arm64-v8a |
| Minimum Android version | Android 9 (API 28) |
| Stock APK SHA-256 used for the off-device check | `128ec75e836f24328d2b28777091c03b20abba0adc536e7ee911ee5fe52e70bc` |

Meta publishes different APKs with the same version name. HushMessenger declares this exact version code. Check the APK you supply before patching. Installing a re-signed app over Meta's original copy is also blocked by Android's normal signing rules; replacing an installed Messenger can remove local app data. [Morphe's keystore guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/backup-and-keystore.md) explains how to keep the key used for future updates.

## Build from source

Install JDK 21 or newer and use the included Gradle wrapper. The Morphe Gradle plugin resolves a package from GitHub Packages, so an authenticated GitHub CLI or equivalent package credentials are needed. On Windows PowerShell:

```powershell
$env:GITHUB_ACTOR = gh api user --jq .login
$env:GITHUB_TOKEN = gh auth token
.\gradlew.bat :patches:buildAndroid
```

Run `.\gradlew.bat :patches:test` to check the manifest rename guard before building a bundle.

The output is `patches/build/libs/patches-0.0.2.mpp`. Morphe Desktop 1.17.0 applied the bundle to a copy of the S22's stock APK. The rebuilt manifest kept all 29 shared-permission mentions under the new names, with none under the old names. Disassembly of the modified DEX found six renamed loads and no active old-name loads in those classes. A signed output passed Android's v3 signature check. The S22's installed Messenger was left untouched.

## Research and related work

[RESEARCH.md](RESEARCH.md) compares the Messenger patches in Morphe, ReVanced, De-Vanced and other sources. [ROADMAP.md](ROADMAP.md) keeps the unfinished checks and patch ideas. HushMessenger shares a visual family and a permission naming contract with [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook); [Hushfeed](https://github.com/SysAdminDoc/hushfeed) is the other Hush patch project.

The build starts from the [Morphe patches template](https://github.com/MorpheApp/morphe-patches-template). The permission approach is adapted from [Hushfacebook's shared-permission patch](https://github.com/SysAdminDoc/Hushfacebook/blob/15b8e9ed9315464a3e2d1a821b4e26ad47bbc28c/patches/src/main/kotlin/app/morphe/patches/facebook/coexist/SharedPermissions.kt). Source is under [GPL-3.0](LICENSE); see [NOTICE](NOTICE). HushMessenger is independent of Meta and Morphe.
