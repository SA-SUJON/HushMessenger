![HushMessenger. Keep the conversation. Cut the friction.](assets/readme-hero.png)

<p align="center">
  <a href="https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.7"><img src="https://img.shields.io/badge/version-0.0.7-0084FF" alt="Version 0.0.7"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License GPL-3.0"></a>
  <img src="https://img.shields.io/badge/platform-Android%209%2B%20arm64-3DDC84" alt="Platform Android 9 or newer, arm64">
  <img src="https://img.shields.io/badge/Messenger-580.0.0.49.91-0084FF" alt="Messenger 580.0.0.49.91">
  <img src="https://img.shields.io/badge/status-preview-8A2BE2" alt="Preview release">
</p>

# HushMessenger

HushMessenger is a preview Morphe patch source for Facebook Messenger. Its first patch renames two shared permissions so a re-signed copy can install beside Meta apps. It doesn't change chats or remove ads yet. You bring the original Messenger APK; this repository provides patch code and a `.mpp` bundle.

**[Add HushMessenger to Morphe Manager](https://morphe.software/add-source?github=SysAdminDoc%2FHushMessenger)**

> [!WARNING]
> **Patched phone behavior is unverified.** We patched, rebuilt and signed a stock APK copy off-device. We haven't tested sign-in, encrypted chats, calls or notifications with a re-signed APK on a phone. Use a test session. A differently signed APK cannot update Meta's installed app, and uninstalling it can remove local data. Keep your signing key and follow [Morphe's backup and keystore guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/backup-and-keystore.md) before replacing anything.

## Get the preview

1. **Check the APK.** This patch targets two arm64 Messenger builds, with version codes `346013387` and `346013440`. The [supported builds](#supported-messenger-builds) have the full details. The version name alone isn't enough.
2. **Add the source.** Open the link above on Android with Morphe Manager installed. You can also open **Sources**, tap **+**, choose **Remote**, and enter `github.com/SysAdminDoc/HushMessenger`.
3. **Check the source.** The HushMessenger card should show one patch. Open **Patches** to find `Install beside Meta apps` for Messenger. Turn on **Pre-release patches** on this card for future preview updates; Morphe's app-update preview setting is separate. Tap the card's refresh button if it stays on an old version.
4. **Choose one source.** Use the remote or local HushMessenger source. Adding both creates two cards with the same name, which can point to different versions. If other sources offer Messenger patches, choose the one you intend; mixing independent patches can cause conflicts.

For a local source, download [`patches-0.0.7.mpp`](https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.7) and add it through **Sources → + → Local**. A local source won't update itself. The `.mpp` file is a patch bundle, not an installable Messenger APK. These source steps follow [Morphe's source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md). A clean Manager 1.32.0 profile imported both v0.0.4 source methods and listed the exact Messenger patch in an isolated Android emulator. The phone behavior warning above still applies.

### If something doesn't work

- **Patch missing:** Check the source card's **Patches** list, turn on **Pre-release patches**, then tap its refresh button.
- **APK rejected:** Use an unmodified arm64 Messenger 580.0.0.49.91 APK with version code `346013387` or `346013440`. If a permission or instruction check fails, the error names the tested builds.
- **Android rejects installation over stock Messenger:** A re-signed APK can't replace Meta's signed copy. Keep your local data intact while you plan a backup. Future updates of your patched copy must reuse your key; see [Morphe's keystore guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/backup-and-keystore.md).
- **Local source still old:** Download the latest `.mpp` and replace the local source yourself.

The S22's stock Messenger 580 has a **Hide suggestions** action in the `People you may know` menu and an **Open links in external browser** switch under **Me → Photos & media**. We found these in a read-only session. Link behavior and the persistence of the suggestions choice still need testing. See the [device notes](Roadmap_Blocked.md#s22-stock-baseline-2026-09-27).

## What the patch changes

### Install beside Meta apps

The patch renames Messenger's two shared Meta signature permissions in declarations, requests, guarded components and six DEX string loads. It stops if those sites differ from the tested APK. It doesn't address Messenger's other cross-app signer checks, so Facebook login and account switching remain on the [roadmap](ROADMAP.md).

Morphe groups these builds under one version name, so it may list the patch for another 580 APK. The patch checks the version code before changing anything and rejects builds other than `346013387` and `346013440`.

If you patch both Messenger and [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook), sign them with the **same key**. Android grants shared signature permissions only when the apps are signed alike.

## Supported Messenger builds

| Field | Value |
| --- | --- |
| Package | `com.facebook.orca` |
| Version | `580.0.0.49.91` |
| Version codes | `346013387` (S22), `346013440` (S25) |
| Architecture | `arm64-v8a` |
| Minimum Android version | Android 9 (API 28) |

SHA-256 of the stock base APKs used for the off-device checks:

```text
346013387  128ec75e836f24328d2b28777091c03b20abba0adc536e7ee911ee5fe52e70bc
346013440  e7d3c64227a7d9a26adda4e89321a87a49c85ee9e9f28f2fa7ed7fa79ae15cf6
```

On Windows, compare your file with `Get-FileHash -Algorithm SHA256 .\messenger.apk`. Meta can publish different APKs under one version name. If the hash differs, don't assume the off-device result applies to your file; the patch also checks its permission layout and instruction sites.

## Verification and build

Twelve unit tests cover the version-code gate, manifest guards, DEX sites and mismatch guidance. Morphe Desktop 1.17.0 applied the bundle to private APK copies from the S22 and S25, rebuilt them and signed them with a test key. Android verified both v3 signatures. Each rebuilt manifest has 26 plus three renamed permission mentions and no old names. The changed-APK check in `scripts/verify_changed_apk_failure.py` confirms that altered permission bytecode in either stock APK stops patching before an output is written. The installed Messenger apps on both phones were left untouched.

To inspect the preview source with Morphe Desktop 1.17.0, set `JAVA_HOME` to a JDK 21 or newer:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar morphe-desktop-1.17.0-all.jar list-patches --patches https://github.com/SysAdminDoc/HushMessenger --prerelease --filter-package-name com.facebook.orca
```

The `--prerelease` flag is needed while there is no stable release. This command lists patches; it doesn't install Messenger.

To build the bundle on Windows, install JDK 21 or newer and use the Gradle wrapper. The Morphe Gradle plugin needs GitHub Packages credentials:

```powershell
$env:GITHUB_ACTOR = gh api user --jq .login
$env:GITHUB_TOKEN = gh auth token
.\gradlew.bat :patches:test :patches:buildAndroid --no-daemon
```

The output is `patches/build/libs/patches-0.0.7.mpp`. Dependency locks and SHA-256 checks are committed. Review both when changing a dependency; clean builds from the same source produce the same bundle checksum.

### Check the bundle

The [v0.0.7 release](https://github.com/SysAdminDoc/HushMessenger/releases/tag/v0.0.7) includes a `SHA256SUMS.txt` file. Compare its `.mpp` hash with your download. You can also build the tagged source locally and compare the output. The checksum and bundle are hosted under the same GitHub account, so this check cannot independently rule out an account compromise.

```text
b7c9cf6a2ce6731382de012b19eaffb5fa121019a23db7626c71e15388ba1364  patches-0.0.7.mpp
```

Morphe Manager 1.32.0 and Desktop 1.17.0 parse `signature_download_url` but do not verify a detached signature when importing patch bundles. An `.asc` link in the source index would not add automatic protection in those versions. Keep the source URL on the repository you trust, and review a new bundle before updating.

## Research and credits

[RESEARCH.md](RESEARCH.md) compares Messenger patches in Morphe, ReVanced, De-Vanced and other projects. [Roadmap_Blocked.md](Roadmap_Blocked.md) tracks the phone checks and proposed patches waiting on an isolated test session. [Hushfeed](https://github.com/SysAdminDoc/hushfeed) is another Hush patch project.

HushMessenger starts from the [Morphe patches template](https://github.com/MorpheApp/morphe-patches-template). The permission approach is adapted from [Hushfacebook's shared-permission patch](https://github.com/SysAdminDoc/Hushfacebook/blob/15b8e9ed9315464a3e2d1a821b4e26ad47bbc28c/patches/src/main/kotlin/app/morphe/patches/facebook/coexist/SharedPermissions.kt). Source is under [GPL-3.0](LICENSE); see [NOTICE](NOTICE). HushMessenger is independent of Meta and Morphe.

<p align="center">
  <a href="https://ko-fi.com/X8K126YVER"><img height="42" src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Buy me a coffee on Ko-fi"></a>
</p>
