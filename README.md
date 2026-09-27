![HushMessenger. Keep the conversation. Cut the friction.](assets/readme-hero.png)

<p align="center">
  <a href="https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.4"><img src="https://img.shields.io/badge/version-0.0.4-0084FF" alt="Version 0.0.4"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License GPL-3.0"></a>
  <img src="https://img.shields.io/badge/platform-Android%209%2B%20arm64-3DDC84" alt="Platform Android 9 or newer, arm64">
  <img src="https://img.shields.io/badge/Messenger-580.0.0.49.91-0084FF" alt="Messenger 580.0.0.49.91">
  <img src="https://img.shields.io/badge/status-preview-8A2BE2" alt="Preview release">
</p>

# HushMessenger

HushMessenger is a preview Morphe patch source for Facebook Messenger. Its first patch renames two shared permissions so a re-signed copy can install beside Meta apps. It doesn't change chats or remove ads yet. You bring the original Messenger APK; this repository provides patch code and a `.mpp` bundle.

**[Add HushMessenger to Morphe Manager](https://morphe.software/add-source?github=SysAdminDoc%2FHushMessenger)**

> [!WARNING]
> **Phone behavior is unverified.** We patched, rebuilt and signed a stock APK copy off-device. We haven't tested sign-in, encrypted chats, calls or notifications on a phone. Use a test session. A differently signed APK cannot update Meta's installed app, and uninstalling it can remove local data. Keep your signing key and follow [Morphe's backup and keystore guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/backup-and-keystore.md) before replacing anything.

## Get the preview

1. **Check the APK.** This patch targets the arm64 Messenger build with version code `346013387`. The [supported build](#supported-messenger-build) has the full details. The version name alone isn't enough.
2. **Add the source.** Open the link above on Android with Morphe Manager installed. You can also open **Sources**, tap **+**, choose **Remote**, and enter `github.com/SysAdminDoc/HushMessenger`.
3. **Enable the preview.** Turn on **Pre-release patches** on the HushMessenger source card. This switch belongs to the source; Morphe's app-update preview setting is separate. Tap the refresh button on the card if the patch isn't listed. **Patches** should show `Install beside Meta apps` for Messenger.
4. **Choose HushMessenger for this app.** If Morphe offers several sources for Messenger, select HushMessenger for this run. Mixing patches from independent sources can cause conflicts.

For a local source, download [`patches-0.0.4.mpp`](https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.4) and add it through **Sources → + → Local**. A local source won't update itself. The `.mpp` file is a patch bundle, not an installable Messenger APK. These source steps follow [Morphe's source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md). A clean Manager 1.32.0 profile imported the previous preview and listed its patch in an isolated Android emulator; the v0.0.4 source refresh still needs a check after publication.

### If something doesn't work

- **Patch missing:** Turn on **Pre-release patches** for this source, then tap **Update** on its card.
- **APK rejected:** Use an unmodified arm64 Messenger 580.0.0.49.91 APK with version code `346013387`. If a permission or instruction check fails, the error names the tested build.
- **Android rejects installation over stock Messenger:** A re-signed APK can't replace Meta's signed copy. Keep your local data intact while you plan a backup. Future updates of your patched copy must reuse your key; see [Morphe's keystore guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/backup-and-keystore.md).
- **Local source still old:** Download the latest `.mpp` and replace the local source yourself.

## What the patch changes

### Install beside Meta apps

The patch renames Messenger's two shared Meta signature permissions in declarations, requests, guarded components and six DEX string loads. It stops if those sites differ from the tested APK. It doesn't address Messenger's other cross-app signer checks, so Facebook login and account switching remain on the [roadmap](ROADMAP.md).

If you patch both Messenger and [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook), sign them with the **same key**. Android grants shared signature permissions only when the apps are signed alike.

## Supported Messenger build

| Field | Value |
| --- | --- |
| Package | `com.facebook.orca` |
| Version | `580.0.0.49.91` |
| Version code | `346013387` |
| Architecture | `arm64-v8a` |
| Minimum Android version | Android 9 (API 28) |

SHA-256 of the stock base APK used for the off-device check:

```text
128ec75e836f24328d2b28777091c03b20abba0adc536e7ee911ee5fe52e70bc
```

On Windows, compare your file with `Get-FileHash -Algorithm SHA256 .\messenger.apk`. Meta can publish different APKs under one version name. If the hash differs, don't assume the off-device result applies to your file; the patch also checks its permission layout and instruction sites.

## Verification and build

Ten unit tests cover the exact manifest guards, DEX sites and mismatch guidance. Morphe Desktop 1.17.0 applied the bundle to a copy of the stock APK, rebuilt it and signed it. Android verified the output's v3 signature. The rebuilt manifest has 29 renamed permission mentions and no old names; the modified DEX has six active renamed loads and no active old loads in those classes. The S22's installed Messenger was left untouched.

To inspect the preview source with Morphe Desktop 1.17.0:

```powershell
java -jar morphe-desktop-1.17.0-all.jar list-patches --patches https://github.com/SysAdminDoc/HushMessenger --prerelease --filter-package-name com.facebook.orca
```

The `--prerelease` flag is needed while there is no stable release. This command lists patches; it doesn't install Messenger.

To build the bundle on Windows, install JDK 21 or newer and use the Gradle wrapper. The Morphe Gradle plugin needs GitHub Packages credentials:

```powershell
$env:GITHUB_ACTOR = gh api user --jq .login
$env:GITHUB_TOKEN = gh auth token
.\gradlew.bat :patches:test :patches:buildAndroid --no-daemon
```

The output is `patches/build/libs/patches-0.0.4.mpp`. Dependency locks and SHA-256 checks are committed. Review both when changing a dependency; clean builds from the same source produce the same bundle checksum.

## Research and credits

[RESEARCH.md](RESEARCH.md) compares Messenger patches in Morphe, ReVanced, De-Vanced and other projects. [ROADMAP.md](ROADMAP.md) tracks the phone checks and proposed patches. [Hushfeed](https://github.com/SysAdminDoc/hushfeed) is another Hush patch project.

HushMessenger starts from the [Morphe patches template](https://github.com/MorpheApp/morphe-patches-template). The permission approach is adapted from [Hushfacebook's shared-permission patch](https://github.com/SysAdminDoc/Hushfacebook/blob/15b8e9ed9315464a3e2d1a821b4e26ad47bbc28c/patches/src/main/kotlin/app/morphe/patches/facebook/coexist/SharedPermissions.kt). Source is under [GPL-3.0](LICENSE); see [NOTICE](NOTICE). HushMessenger is independent of Meta and Morphe.

<p align="center">
  <a href="https://ko-fi.com/X8K126YVER"><img height="42" src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Buy me a coffee on Ko-fi"></a>
</p>
