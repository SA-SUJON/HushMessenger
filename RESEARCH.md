# Research: HushMessenger

Date: 2026-09-27. Replaces all prior research.

## Executive Summary

HushMessenger v0.3.0 provides 21 selectable Morphe patches, including 20 optional controls, for two exact arm64 Messenger 580 builds. Its useful distinction is a small native settings extension with conservative target checks. The largest unresolved problem is original-package startup after re-signing: a successful patch, signature check or standalone settings test doesn't establish working Messenger. Prioritize that existing acceptance gate, then fix the concrete metadata and validation gaps below. [Verified: `README.md`, `MessengerTarget.kt`, `Roadmap_Blocked.md`; Needs live validation: original-package runtime.]

Highest-value opportunities, in order:

1. Resolve original-package startup and recovery through the existing P0 items; preserve both stock sign-ins. [`Roadmap_Blocked.md`]
2. Check permission protection levels and validate an entire optional patch before modifying methods or publishing its installed flag. [`InstallBesideMetaAppsPatch.kt`, `MessengerControlsPatch.kt`]
3. Restore the exact-build description lost through Morphe's legacy constructor. [`MessengerTarget.kt`, [Patcher 1.14.1](https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/patch/Compatibility.kt)]
4. Test actual control discovery, including absent and ambiguous targets. [`ControlHooks.kt`, `ExpandedControlsTest.kt`]
5. Prove settings remain usable in short windows, with the keyboard and maximum text size. [`SettingsNavigationTest.java`, [Android guidance](https://support.google.com/accessibility/android/answer/12159181?hl=en-GB)]
6. Make eligibility, saved choices and resumed settings agree. [`Settings.java`, `SettingsActivity.java`]
7. Add native compatibility diagnostics and a generated public patch catalog. [`scripts/check_install.py`, [Morphe catalog task](https://github.com/MorpheApp/morphe-patches/blob/main/patches/build.gradle.kts)]
8. Add settings accessibility semantics and localization without changing preference keys or borrowing Messenger resource IDs. [`SettingsUi.java`, [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/principles-views)]

## Product Map

**Verified workflows and boundaries** (`README.md`, `patches-bundle.json`, `SettingsActivity.java`, `scripts/check_install.py`):

- Add the source in Manager or Desktop, supply a stock APK, select patches, rebuild and sign locally.
- Check candidate and installed certificates before considering installation. The checker never installs or clears data.
- Open HushMessenger settings from the app drawer. Controls has search, category filters and pause; App has appearance and setup information.
- Rebuild Messenger to install additional controls. Updating the Morphe source alone doesn't alter the installed app.

**Personas:** Messenger users who want fewer distractions, cautious users maintaining a patched Meta app pair, and maintainers validating new APK variants. These are design assumptions inferred from `README.md` and [issue 1](https://github.com/SysAdminDoc/HushMessenger/issues/1), not measured audience segments.

**Platform and distribution:** Android 9+; arm64; `com.facebook.orca` 580.0.0.49.91 codes 346013387 and 346013440. The deliverable is an MPP containing patch classes and an MPE extension. It is not a redistributed Messenger APK. Preserve GPL-3.0, the Morphe naming requirements and the Messenger Cleaner MIT notice. [Verified: `MessengerTarget.kt`, `patches/build.gradle.kts`, `LICENSE`, `NOTICE`]

**Data flow:** Kotlin patches inspect and modify the supplied APK; a private provider initializes Java preferences; injected hooks read installed-feature flags and switches. Settings are local to an app installation and shared across its Messenger accounts. Host messaging, encrypted history and account credentials remain Messenger responsibilities. [Verified: `Settings.java`, `SettingsProvider.java`, `MessengerControlsPatch.kt`]

Source paths below abbreviate these roots: `patches/src/main/kotlin/app/hushmessenger/patches/`, its matching test tree, and `extensions/messenger/src/main/java/app/hushmessenger/extension/`, with tests under `src/test/java/`.

## Competitive Landscape

Repository and vendor statements establish documented capabilities, not successful behavior on HushMessenger's exact APKs.

| Project or class | Useful evidence | Learn; avoid |
| --- | --- | --- |
| [De-Vanced](https://github.com/RookieEnough/De-Vanced) | Independent Messenger controls and feature contributions; [issue 106](https://github.com/RookieEnough/De-Vanced/issues/106) discusses broad version matching. | Reuse individually validated hooks with attribution. Don't declare every APK sharing a version name compatible. |
| [Doom/Rushi](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md) | Broad patch catalog; [issue 979](https://github.com/rushiranpise/morphe-patches/issues/979) reports failure on code 346013438. | Treat each catalog entry as a lead. That report doesn't validate or invalidate codes 346013387/440. |
| [ReVanced](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/api/patches.api) | Patch metadata and shared extension patterns. | Keep independent selection and reuse boundaries. Avoid transferring target assumptions between forks. |
| [Messenger Cleaner](https://github.com/N01-r0/messenger-cleaner-lsposed) | Typed inbox-item filtering, already adapted here. | Preserve normal conversations and original-list behavior. Its runtime-hook environment doesn't prove MPP compatibility. |
| [MessengerPro](https://github.com/Mino260806/MessengerPro/issues/70), [MessengerEx](https://github.com/C10udburst/MessengerEx) | E2EE suppression reports and distinct link-privacy controls. | Verify encrypted chats and safety warnings. Don't confuse UI privacy switches with transport guarantees. |
| [ChatHeadEnabler](https://github.com/NeonOrbit/ChatHeadEnabler), [NoBrowserFacebookMessenger](https://github.com/nlevi-dev/NoBrowserFacebookMessenger) | Focused bubble and browser hooks. | Separate OS eligibility from app choices. Existing Hush bubble/link acceptance already covers these features. |
| [Messenger-Z](https://github.com/hyowonbernabe/Messenger-Z/blob/main/docs/hook-ids.md) | Hook-ID maintenance notes explicitly leave some newer IDs uncaptured. | Record hook evidence per build. A fresh APK release isn't proof that every native E2EE hook works. |
| [MRVPatcher](https://github.com/NeonOrbit/MRVPatcher), [Messenger-Revanced](https://github.com/mentalblank/Messenger-Revanced) | Build/signing tools and prebuilt distribution approaches. | Sign-only controls can inform the existing startup investigation. Keep Hush distribution limited to patch code and bundles. |
| [Meridian](https://github.com/meridianfresco/morphe-meta-patches/issues/24), [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook) | Partner-app trust and login constraints. | Preserve full signer checks and recovery. Renaming a permission alone doesn't establish cross-app trust. |
| [Morphe reference catalog](https://github.com/MorpheApp/morphe-patches/blob/main/patches-list.json), [Awesome Morphe](https://github.com/nvbangg/awesome-morphe/blob/main/CONTRIBUTING.md) | Machine-readable patch descriptions; the index falls back to parsing bundles without a catalog. | Generate a catalog locally from the built bundle. Don't copy upstream hosted build workflows. |
| [Beeper](https://help.beeper.com/beeper-plus/beeper-plus-and-beeper-plus-plus-faqs), [Texts migration](https://help.beeper.com/en_US/beeper-plus/subscriptions-how-legacy-beeper-and-texts-users-are-transitioning-to-the-new-beeper) | Beeper Plus is USD 9.99/month and charges for additional accounts and organization features. Texts has stopped new registrations. | Explain local/account boundaries clearly. A unified inbox or transcription service would add account and data handling unrelated to this patch source. |
| [Friendly](https://friendly.io/), [Rambox](https://rambox.app/pricing/) | Friendly advertises keyword and ad filtering. Rambox includes focus mode free; Pro is USD 7/month. | Keep basic distraction controls understandable. Keyword filtering needs a demonstrated host gap before risking hidden conversations. |

[Verified: linked source statements, checked 2026-09-27. Product-fit conclusions are recommendations.]

## Reported Issues

**[Issue 1](https://github.com/SysAdminDoc/HushMessenger/issues/1)** is the sole open HushMessenger report on 2026-09-27. The log identifies code 346013370 and the deliberate rejection in `coexist/InstallBesideMetaAppsPatch.kt::validateVersionCode`. Its two comments provide no stock APK or additional technical reproduction. Keep the existing blocked fixture-validation item; don't weaken the guard. The lost compatibility description is a separate verified defect that can make the distinction harder to understand. [Verified: issue thread, source and released MPP constructor call.]

There are no closed issues, open PRs or enabled discussions to mine for resolved requests. [Verified: [repository API](https://api.github.com/repos/SysAdminDoc/HushMessenger), [issues](https://github.com/SysAdminDoc/HushMessenger/issues?q=is%3Aissue), [PRs](https://github.com/SysAdminDoc/HushMessenger/pulls)]

External reports reinforce existing work, without becoming new Hush bug claims:

- [MessengerPro 70](https://github.com/Mino260806/MessengerPro/issues/70) reports suppression failing in encrypted chats. Keep the two-account typing acceptance; it doesn't report decryption corruption.
- [AppleVis](https://www.applevis.com/forum/assistive-technology/meta-has-changed-accessibility-experience-messenger-again-web-time) and the [Samsung font report](https://www.reddit.com/r/SamsungGalaxyS26U/comments/1vmq86m/question_about_font_size_on_samsung_galaxy_s26/) support the blocked host accessibility checks. They don't prove Hush settings fail.
- [Morphe catalog feedback](https://www.reddit.com/r/MorpheApp/comments/1s6ko2c/see_list_of_patch_available/) asks to inspect patches before obtaining an APK. This supports a generated catalog.
- [Beeper Android UI feedback](https://www.reddit.com/r/beeper/comments/1ux4n4u/on_the_subject_of_the_new_android_ui/) describes reduced usable space and hidden navigation. Device reports disagree; use it to choose layout tests, not to claim prevalence.

[Verified: reports exist. Needs live validation: applicability to this project.]

## Security, Privacy, and Reliability

**Permission contract.** `coexist/InstallBesideMetaAppsPatch.kt::renameSharedPermissions` validates declaration counts, owners and uses, but never checks `protectionLevel`. Its positive test fixture omits that attribute. Both stock fixtures declare exactly signature level (0x00000002), without additional flags. A weakened declaration can therefore pass the structural checks. Validate both declarations against the exact fixtures before mutation, including normalized numeric/text values. This is a missing guard, not evidence that the released APK weakened its permissions. [Verified: source and `InstallBesideMetaAppsPatchTest.kt`; [Android permission semantics](https://developer.android.com/guide/topics/manifest/permission-element)]

**Partial failure.** `controls/MessengerControlsPatch.kt::controlPatch` adds feature metadata through a dependency, then validates and injects methods sequentially. A later plugin-gate failure can leave earlier mutations and the installed flag in the shared context. Patcher executes dependencies first and doesn't roll back that context; Desktop's `--continue-on-error` path can export it. Default Desktop aborts on failure. Prevalidate every selected method and publish capability metadata only after success. [Verified: local source, [Patcher 1.14.1](https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/Patcher.kt), [Desktop execution](https://github.com/MorpheApp/morphe-desktop/blob/v1.17.0/src/main/kotlin/app/morphe/desktop/command/PatchCommand.kt#L801); altered-control exported-APK reproduction still required.]

**Startup and recovery.** The zero-patch rebuild also showed a blank screen, but it was installed over the failed patched app and shared its data. It wasn't an independent clean-state control. A private renamed diagnostic reached the inbox; that doesn't establish original-package behavior. Keep installation, new-message delivery, historical encrypted-history recovery and same-key rollback as separate gates. [Verified: `Roadmap_Blocked.md`; [Messenger E2EE architecture](https://engineering.fb.com/2023/12/06/security/building-end-to-end-security-for-messenger/)]

**Native packaging.** `scripts/check_install.py` doesn't compare APK and device ABIs. Both private stock fixtures and both v0.3.0 unsigned rebuilds contain 13 compressed arm64 libraries with minimum ELF load-segment alignment of 16384. No alignment defect was demonstrated. Add diagnostics and preservation tests, not a claimed startup repair; compressed library entries don't require the same ZIP alignment as directly mapped libraries. [Verified: read-only fixture inspection; [Android page sizes](https://developer.android.com/guide/practices/page-sizes)]

**Dependency boundary.** `patches/gradle.lockfile` includes Bouncy Castle 1.79, matching advisories such as [CVE-2026-13506](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%9013506) and [CVE-2026-8763](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%908763). The released MPP/MPE contains no BC classes or references. The [plugin](https://github.com/MorpheApp/morphe-patches-gradle-plugin/blob/v1.3.4/src/main/kotlin/app/morphe/patches/gradle/PatchesPlugin.kt) excludes patcher-provided dependencies; the [signer](https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/apk/ApkSigner.kt) uses BC for keystore/certificate operations. Vulnerable CRL or name-constraint paths were not established. A local version override wouldn't update a user's Manager/Desktop runtime. [Verified: versions and packaging; Needs runtime validation: exploitability.]

**Settings privacy.** The extension has no chat export or network reporting. Any support summary should be copied only on request and contain version/capability information, excluding account IDs, contacts, messages, tokens and recovery material. `SharedPreferences.Editor.apply()` cannot report disk-write failures, while the UI claims automatic saving; fault-inject before designing a background durability path. [Verified: `SettingsActivity.java`, [Android API](https://developer.android.com/reference/android/content/SharedPreferences.Editor); Needs validation: actual write failure.]

**Existing safeguards remain required.** Link cleanup must preserve [Messenger browsing protection](https://engineering.fb.com/2026/03/09/security/how-advanced-browsing-protection-works-in-messenger/); hiding AI entry points isn't a data-processing guarantee. Manager 1.32.0 and Desktop 1.17.0 don't verify the detached-signature field, so keep the checksum limitations in `README.md`. The [developer-verification FAQ](https://developer.android.com/developer-verification/guides/faq) limits 2026-09-30 enforcement to participating stores; direct sideloads are unchanged in that phase and ADB remains exempt. Broader rollout is planned for 2027 without an exact day specified there. [Verified: cited platform guidance and `README.md`; no new duplicate distribution item.]

## Architecture Assessment

The Kotlin patch/Java extension/Python preflight split fits the project. Keep framework views and existing test tools. `MessengerTarget.kt` calls the legacy `AppTarget(version, isExperimental, minSdk, description)` overload, which discards the description. Explicit `versionCodes = null` selects the primary constructor without inventing ABI entries or accepting another build. [Verified: released bytecode and [pinned constructor](https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/patch/Compatibility.kt).]

The release evidence records 57 passing tests, but no Kotlin test invokes `findControls`; manually constructed match maps bypass discovery. Python orchestration tests mock `read_apk`, leaving its tool-output parsing insufficiently covered. Settings tests mainly use API 35; the large-text case checks child bounds at 320 by 800, not a usable visible viewport. Add targeted negative cases and API 28/36 boundaries. [Verified: `README.md`, `ExpandedControlsTest.kt`, `scripts/tests/test_check_install.py`, `SettingsNavigationTest.java`; [Robolectric 4.17](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17)]

The four `assets/settings*.png` captures show coherent dark/light Controls and App pages; no new palette contrast defect was found. Fixed header/footer space may exhaust a short window at 200% text or with the keyboard open. Geometry failure is still a hypothesis. Test global visibility and scrolling, following [Android scaling guidance](https://support.google.com/accessibility/android/answer/12159181?hl=en-GB) and the screenshot-plus-structure approach in [AccessiText](https://ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf). No new visual framework is needed. [Verified: captures and `SettingsActivity.java`; Likely: short-window risk.]

`Settings.enableBubbles` returns false below API 30, while `SettingsActivity.updateSetup` counts saved flags without that eligibility check. The activity also lacks resumed-state rebinding and explicit search-selection restoration. Category/page display strings act as identifiers; physical left margins and English literals obstruct localization. Use stable IDs, lifecycle-aware binding and a small resource-independent text catalog. Test accessibility nodes and RTL behavior before promising either. [Verified: `Settings.java`, `SettingsActivity.java`; [Android state](https://developer.android.com/topic/libraries/architecture/views/activity-lifecycle-views), [pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales)]

### Delivery order

Scores express recommended user impact from 1 to 5. S/M estimate implementation effort, not certainty. All additions fit the existing local patch model and address reliability or usability parity. Evidence is in the findings above and the matching `ROADMAP.md` item.

| Tier | Addition | Impact / effort | Risk, prerequisite and reason for placement |
| --- | --- | --- | --- |
| Now | Permission-level contract | 5 / S | Security boundary; exact fixture flags first. Small missing guard. |
| Now | Validate before mutation/publication | 5 / M | Partial output can misrepresent installed controls; reproduce a late failure first. |
| Now | Preserve target description | 4 / S | Low risk; keep the runtime version guard. Directly addresses confusing compatibility metadata. |
| Now | Resolver regression fixtures | 5 / M | False matches can affect host behavior; cover discovery before adding hooks. |
| Now | Short-window/API layout coverage | 4 / M | Accessibility parity; reproduce with existing harness before changing layout. |
| Next | Bubble eligibility feedback | 4 / S | Low risk; distinguish saved state from API eligibility without granting OS permissions. |
| Next | Resume/editing state | 3 / M | Listener/recreation loops are the risk; preserve existing preference keys. |
| Next | Settings accessibility semantics | 4 / M | Focus regressions need tree/action tests; follow the layout checks. |
| Next | APK/native preflight and parsers | 4 / M | False approval is the risk; malformed input must fail without device mutation. |
| Next | Local catalog and metadata checks | 3 / M | Ecosystem parity; fix the description before generating it. |
| Next | Private support summary/scope help | 3 / S | Exclude personal data; reuse eligibility semantics. Reduces incomplete reports. |
| Next | Stable locale IDs and RTL | 3 / M | Resource collision and state migration risks; retain framework-only integration. |
| Later | Durable preference feedback | 3 / M | No loss reproduced; fault injection and correct write ordering precede a storage change. |

Dependency review found no reason to replace the pinned toolchain speculatively. Patcher 1.14.1 is stable; 1.15.0-dev.4 fixes a different description overload. Plugin 1.3.4 and Robolectric 4.17 meet the proposed work. Kotlin 2.4.20 and Gradle 9.8.0 exist, but neither establishes a fix for this app. Keep plugin-managed AGP, smali and ARSCLib changes behind the exact-APK rebuild/signing checks. [Verified: `gradle/`, lockfiles, [Patcher PR 222](https://github.com/MorpheApp/morphe-patcher/pull/222), [Kotlin release](https://github.com/JetBrains/kotlin/releases/tag/v2.4.20), [Gradle release](https://github.com/gradle/gradle/releases/tag/v9.8.0)]

**Coverage decisions:** security and reliability lead; accessibility/i18n target the native settings boundary; observability stays user-initiated; distribution and plugin discovery gain a generated catalog. Offline message queues and per-account storage would duplicate host responsibilities without a local failure. Preserve keys for upgrades and locale changes. Settings-only import/export is under consideration after durable writes, with an allowlisted schema and no host data, but direct demand is too weak to schedule. Broader translations need reviewed text. Existing `Roadmap_Blocked.md` items retain recovery, link safety, notifications and live patch acceptance. [Recommendation grounded in `Settings.java`, `Roadmap_Blocked.md` and the competitive evidence.]

## Rejected Ideas

- **Any-version compatibility or skipped missing hooks:** exact variants already diverge. Retain pre-mutation rejection. [Verified: [De-Vanced 106](https://github.com/RookieEnough/De-Vanced/issues/106), `MessengerTarget.kt`]
- **Restore the old inbox-ad loader:** it is absent in the checked fixtures. Validate the existing typed filter on an affected account. [Verified: `ControlHooks.kt`, [Messenger Cleaner](https://github.com/N01-r0/messenger-cleaner-lsposed)]
- **Market read-receipt switches or HD sending as new:** stock already documents them. Reconsider only after a demonstrated stock gap. [Verified: [Meta privacy controls](https://about.fb.com/news/2023/12/default-end-to-end-encryption-on-messenger/amp/), [media features](https://about.fb.com/news/2024/04/hd-photos-shared-albums-and-more-on-messenger/)]
- **Unified inbox, cloud transcription or a messaging bridge:** these require new credential, storage and protocol boundaries. Paid competitor demand doesn't justify that change to a patch source. [Recommendation: [Beeper FAQ](https://www.beeper.com/faq), [Meta interoperability engineering](https://engineering.fb.com/2024/03/06/security/whatsapp-messenger-messaging-interoperability-eu/)]
- **Mandatory root, public modified Meta APKs, or package cloning as a startup fix:** conflict with the supported distribution and identity model. [Recommendation: `README.md`, `Roadmap_Blocked.md`, [Messenger-Z](https://github.com/hyowonbernabe/Messenger-Z)]
- **Maximum version-code spoofing, bypassing link warnings or one-time-media safeguards:** obscure update truth or weaken recipient protections. [Recommendation: `MessengerTarget.kt`, [Messenger browsing protection](https://engineering.fb.com/2026/03/09/security/how-advanced-browsing-protection-works-in-messenger/)]
- **Blanket dependency upgrades or automatic host-library replacement:** a version match is not proof of reachable vulnerable code, and bundle dependencies don't update installed patcher hosts. [Recommendation: [LibBandAid research](https://www.usenix.org/conference/raid2019/presentation/duan), plugin/signer sources above.]
- **Scheduled profiles, keyword hiding and settings import/export now:** weak direct demand and extra background/state complexity. Keep under consideration until the local reliability work is complete. [Assumption: [Friendly](https://friendly.io/), [Rambox features](https://rambox.app/features/), `Settings.java`]

## Sources

### Project and patch ecosystem

https://github.com/SysAdminDoc/HushMessenger/issues/1
https://github.com/RookieEnough/De-Vanced
https://github.com/rushiranpise/morphe-patches/issues/979
https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/api/patches.api
https://github.com/N01-r0/messenger-cleaner-lsposed
https://github.com/Mino260806/MessengerPro/issues/70
https://github.com/C10udburst/MessengerEx
https://github.com/hyowonbernabe/Messenger-Z/blob/main/docs/hook-ids.md
https://github.com/MorpheApp/morphe-patches/blob/main/patches/build.gradle.kts
https://github.com/Jman-Github/Awesome-ReVanced
https://github.com/nvbangg/awesome-morphe/blob/main/CONTRIBUTING.md

### Products and community

https://help.beeper.com/beeper-plus/beeper-plus-and-beeper-plus-plus-faqs
https://help.beeper.com/en_US/beeper-plus/subscriptions-how-legacy-beeper-and-texts-users-are-transitioning-to-the-new-beeper
https://friendly.io/
https://rambox.app/pricing/
https://www.reddit.com/r/MorpheApp/comments/1s6ko2c/see_list_of_patch_available/
https://www.reddit.com/r/beeper/comments/1ux4n4u/on_the_subject_of_the_new_android_ui/
https://www.applevis.com/forum/assistive-technology/meta-has-changed-accessibility-experience-messenger-again-web-time

### Platform, engineering and security

https://developer.android.com/guide/topics/manifest/permission-element
https://developer.android.com/guide/practices/page-sizes
https://developer.android.com/developer-verification/guides/faq
https://developer.android.com/reference/android/content/SharedPreferences.Editor
https://developer.android.com/guide/topics/ui/accessibility/views/principles-views
https://developer.android.com/guide/topics/resources/pseudolocales
https://support.google.com/accessibility/android/answer/12159181?hl=en-GB
https://ics.uci.edu/~seal/publications/2022_FSE_AccessiText.pdf
https://arxiv.org/abs/2212.04388
https://engineering.fb.com/2023/12/06/security/building-end-to-end-security-for-messenger/
https://engineering.fb.com/2026/03/09/security/how-advanced-browsing-protection-works-in-messenger/
https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/patch/Compatibility.kt
https://github.com/MorpheApp/morphe-patcher/pull/222
https://github.com/MorpheApp/morphe-patches-gradle-plugin/blob/v1.3.4/src/main/kotlin/app/morphe/patches/gradle/PatchesPlugin.kt
https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%9013506
https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%908763

## Open Questions

- What causes the original-package blank startup under a faithful stock/rebuilt/re-signed comparison with independent state and a verified recovery route? The existing update-over-failed-install control doesn't isolate all variables. [`Roadmap_Blocked.md`]
- Can the reporter supply the untouched, correctly signed 346013370 APK so its contracts can be compared? Neither its version name nor another project's 346013438 report substitutes for that fixture. [Issue 1]
- Which affected-account rows and encrypted-chat routes exercise the existing ad, story and typing hooks? Before/after observations remain necessary even when the off-device contracts pass. [`Roadmap_Blocked.md`, `README.md`]
