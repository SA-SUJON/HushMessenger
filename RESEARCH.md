# Research: HushMessenger

Date: 2026-09-27. This replaces earlier scoping notes for this repository.

## Summary

HushMessenger v0.0.2 is a Morphe patch source for Messenger `com.facebook.orca`. Its first target is the stock S22 arm64 APK, version `580.0.0.49.91` and version code `346013387`. Existing ReVanced and Morphe projects cover inbox ads, Meta AI, navigation, typing indicators and chat heads, but their advertised targets or bytecode hooks do not establish that those patches work on this APK. The strongest first steps are reliable patch application, safe coexistence with Facebook, a traced replacement for the old inbox ad hook, and a small settings surface for optional changes.

## Product map

- A user supplies their own stock Messenger APK to a Morphe compatible patcher.
- The patch bundle changes only matched APK versions, then the patcher rebuilds and signs an installable app with the user's key.
- The initial patch renames two shared Meta signature permissions in the manifest and DEX. This preserves the component guards that would be lost by deleting the declarations.
- Users may have stock Facebook, patched Hushfacebook, or other Meta apps installed. A patched Messenger paired with patched Hushfacebook must use the same signing key because both declare the `app.hushfacebook.*` permission names.
- The source and patch bundle are distributable. Stock or modified Messenger APKs and signing keys are not repository assets.

## Existing projects

| Project | What exists | Lesson for HushMessenger |
| --- | --- | --- |
| [Morphe patch template](https://github.com/MorpheApp/morphe-patches-template) | Gradle plugin, patch bundle packaging and Kotlin patch DSL | Use its current structure and produce a Manager compatible `.mpp`. Keep the source independent of the Facebook bundle. |
| [ReVanced patches](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/api/patches.api?ref_type=heads) | Public API lists Messenger inbox ads, inbox subtabs, Facebook button and Meta AI patches | Treat these as candidate behaviors. A patch name or `all` compatibility label does not prove that its anchor survives Messenger 580. |
| [De-Vanced](https://github.com/RookieEnough/De-Vanced) | Morphe source with typing, inbox, AI, link and version patches | Its [newer inbox fix](https://github.com/RookieEnough/De-Vanced/commit/de0a110ac8ce36423144c841a0f1ec6e05383356) skips the hook when the old loader is absent. An apparent successful patch run can still leave ads visible. |
| [Rushi's Morphe patches](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md) | Broad Messenger menu including chat heads, media quality, stories, AI and ads; catalog lists Messenger 576 | Useful behavior map, but its [update tracker reports 580 broken](https://github.com/rushiranpise/morphe-patches/issues/979). Port each hook only after a 580 fixture check and a visible app check. |
| [Meridian's Meta patches](https://github.com/meridianfresco/morphe-meta-patches/blob/main/CHANGELOG.md) | Earlier Messenger patches for typing, ads, tabs, Facebook entry points and AI | Another source of implementations; [duplicate-permission reports](https://github.com/meridianfresco/morphe-meta-patches/issues/24) underline the install problem. |
| [MessengerEx](https://github.com/C10udburst/MessengerEx) and [Messenger Cleaner](https://github.com/N01-r0/messenger-cleaner-lsposed/blob/main/README.md) | Runtime hooks that hide AI, ads and menu promotions; the latter names Messenger 556 | They expose UI surfaces worth checking, but LSPosed requires a different device setup and old hooks need revalidation. |
| [NeonOrbit ChatHeadEnabler](https://github.com/NeonOrbit/ChatHeadEnabler) and [MRVPatcher](https://github.com/NeonOrbit/MRVPatcher) | Focused feature enablement and multi-app signing approaches | Check chat heads and cross-app signer behavior against actual supported Android versions before adoption. |
| [Messenger ReVanced autobuild](https://github.com/mentalblank/Messenger-Revanced) | Packages modified Messenger APKs, with the latest listed build on 563 | It documents user demand and update friction. HushMessenger should distribute patch source and bundles without bundling Meta APKs. |

## Evidence from the target APK

The S22 supplied an installed, stock arm64 base APK on 2026-09-26. Its SHA-256 is `128ec75e836f24328d2b28777091c03b20abba0adc536e7ee911ee5fe52e70bc`. It is Messenger 580, version code `346013387`, minSdk 28, targetSdk 36. The Android manifest declares `com.facebook.permission.prod.FB_APP_COMMUNICATION` and `com.facebook.receiver.permission.ACCESS`. They also appear in uses and component guards. Meta's signing certificates rotate by Android API level, so version name alone is an inadequate compatibility gate. The APK copy is a local, ignored test input.

The earlier Messenger 580 scoping pass in `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` inspected a different arm64 variant, version code `346013445`. It found the old `InboxAdsItemSupplierImplementation` loading strings absent while sponsored-thread fields and Montage ad models remain. Those DEX findings are leads for this exact S22 variant, not proof that the same offsets or rendered rows match. [De-Vanced issue 57](https://github.com/RookieEnough/De-Vanced/issues/57) and [ReVanced Manager issue 3218](https://github.com/ReVanced/revanced-manager/issues/3218) show why an ad patch must be checked on screen after it applies.

## Reported issues

No issues have been filed in this new repo yet. Reports in the closest projects are specific enough to guide the first checks:

- [De-Vanced 96](https://github.com/RookieEnough/De-Vanced/issues/96), [63](https://github.com/RookieEnough/De-Vanced/issues/63) and [Meridian 24](https://github.com/meridianfresco/morphe-meta-patches/issues/24) describe install conflicts with Facebook. Meridian 24 also reports a failure at Messenger's two-factor login step after installation. These are separate coexistence and trust checks.
- [De-Vanced 57](https://github.com/RookieEnough/De-Vanced/issues/57), [17](https://github.com/RookieEnough/De-Vanced/issues/17) and [2](https://github.com/RookieEnough/De-Vanced/issues/2) record inbox-ad fingerprint failures on builds after the old loader changed. [Issue 95](https://github.com/RookieEnough/De-Vanced/issues/95) shows patch completion can still end in install trouble. [Issue 106](https://github.com/RookieEnough/De-Vanced/issues/106) asks for any-version support, but the thread itself warns that Meta variants differ. Keep an exact supported target until later versions pass the same checks.
- [De-Vanced 97](https://github.com/RookieEnough/De-Vanced/issues/97) documents an external-link fingerprint failure. [Issue 38](https://github.com/RookieEnough/De-Vanced/issues/38) asks to hide People You May Know to avoid accidental friend requests. [Issues 47](https://github.com/RookieEnough/De-Vanced/issues/47) and [91](https://github.com/RookieEnough/De-Vanced/issues/91) ask for native bubbles or chat heads. Those are two different UI modes and should not be conflated.
- [Rushi 979](https://github.com/rushiranpise/morphe-patches/issues/979) reports Messenger 578 and 580 breaking after a 576 working build. [Rushi 1004](https://github.com/rushiranpise/morphe-patches/issues/1004) asks for story downloads. [ReVanced Manager 3324](https://github.com/ReVanced/revanced-manager/issues/3324) reports a failed, lengthy Messenger patch run on a lower-memory phone. Off-device verification and clear failure reports matter.

## Reliability and privacy

- A renamed shared permission must be changed in every declaration, request, component guard and DEX literal. The [Android manifest permission specification](https://developer.android.com/guide/topics/manifest/permission-element) makes a duplicate declaration under different signers an install conflict. Removing the declaration would also change component protection.
- A re-signed Messenger needs separate validation for screens and Facebook sign-on. Renaming permissions alone does not make Meta's own signature checks trust the new signer. The [Hushfacebook shared-permission implementation](https://github.com/SysAdminDoc/Hushfacebook/blob/15b8e9ed9315464a3e2d1a821b4e26ad47bbc28c/patches/src/main/kotlin/app/morphe/patches/facebook/coexist/SharedPermissions.kt) supplied the naming contract.
- Ad rows must be identified before filtering. Hiding a sponsored business thread by broad type could also hide a user's legitimate conversation.
- Patches that change typing status, capture notices or media handling affect other people. They should be individually selectable with clear descriptions and never silently bundled into a privacy promise.
- Each upgrade needs a freshly identified APK fixture, patch application report, manifest comparison and signed installation check. The current off-device check cannot establish login, notifications, calls or chat heads.

## Architecture assessment

The new source has one exact target object and one patch. It has no extension, tests or release automation. The bundle build and a real off-device patch run are the current verification. Keep target facts in `MessengerTarget.kt`, add fixture-driven checks before supporting a second APK variant, and add an Android 9 compatible settings module only when a patch needs a user switch. The first verification script should inspect rebuilt manifest attributes and active DEX instructions, since unused string-pool values remain after `STRIP_FAST`.

## Rejected approaches

- Port the old inbox ad loader unchanged. The 580 scoping pass did not find its loader strings; [the current De-Vanced fix](https://github.com/RookieEnough/De-Vanced/commit/de0a110ac8ce36423144c841a0f1ec6e05383356) permits a no-op.
- Make package cloning the default. [De-Vanced issue 63](https://github.com/RookieEnough/De-Vanced/issues/63) and the target manifest show that package identity and provider permissions matter to Facebook integration. Keep the stock package name for the first bundle.
- Ship a modified Meta APK in this repository. It would obscure which stock build users patched and require redistribution of Meta's binary. Ship a source bundle and a repeatable local verification process.
- Disable screenshot notices or bypass one-time media limits by default. [Rushi's catalog](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md) lists these, but they change expectations for other chat participants and are not needed for the first user experience fixes.

## Sources

Patch projects: https://github.com/MorpheApp/morphe-patches-template ; https://github.com/MorpheApp/morphe-patches-gradle-plugin ; https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/api/patches.api?ref_type=heads ; https://github.com/RookieEnough/De-Vanced ; https://github.com/rushiranpise/morphe-patches ; https://github.com/meridianfresco/morphe-meta-patches ; https://github.com/C10udburst/MessengerEx ; https://github.com/N01-r0/messenger-cleaner-lsposed ; https://github.com/NeonOrbit/ChatHeadEnabler ; https://github.com/NeonOrbit/MRVPatcher

Reports and platform: https://github.com/RookieEnough/De-Vanced/issues/57 ; https://github.com/RookieEnough/De-Vanced/issues/63 ; https://github.com/RookieEnough/De-Vanced/issues/96 ; https://github.com/RookieEnough/De-Vanced/issues/97 ; https://github.com/RookieEnough/De-Vanced/issues/38 ; https://github.com/RookieEnough/De-Vanced/issues/47 ; https://github.com/rushiranpise/morphe-patches/issues/979 ; https://github.com/rushiranpise/morphe-patches/issues/1004 ; https://github.com/meridianfresco/morphe-meta-patches/issues/24 ; https://github.com/ReVanced/revanced-manager/issues/3218 ; https://github.com/ReVanced/revanced-manager/issues/3324 ; https://developer.android.com/guide/topics/manifest/permission-element

## Open questions

- What exact chat-list row does an affected signed-in account see as an ad on Messenger 580? A screenshot and row trace are needed to choose the filter.
- Does a newly signed Messenger preserve login, calls, notifications and Facebook cross-app flows when paired with stock Facebook or same-key Hushfacebook? This needs an isolated device session or a user-run phone check.
