# HushMessenger roadmap

Original-package startup, account recovery and cross-app acceptance remain the P0 gates in [Roadmap_Blocked.md](Roadmap_Blocked.md). The unsupported build in [issue 1](https://github.com/SysAdminDoc/HushMessenger/issues/1) also retains its existing fixture-validation item there. Keep those gates separate from the local implementation work below.

## Research-Driven Additions

### P1: Now

- [ ] P1: Enforce signature protection on both renamed permissions.
  Why: The manifest validator accepts declarations without a protection level despite protecting shared components.
  Evidence: `coexist/InstallBesideMetaAppsPatch.kt::renameSharedPermissions`, its positive test fixture, and [Android permission rules](https://developer.android.com/guide/topics/manifest/permission-element). Both supported stock APKs declare exactly signature (0x00000002).
  Touches: `patches/src/main/kotlin/app/hushmessenger/patches/coexist/InstallBesideMetaAppsPatch.kt`; matching Kotlin tests.
  Acceptance: Normalize numeric and symbolic protection levels. Accept the exact signature level; reject missing, normal, dangerous and extra flags before any mutation. Both stock fixtures retain their levels and existing owner/site checks pass.
  Complexity: S

- [ ] P1: Validate complete controls before editing methods or publishing capabilities.
  Why: A late failure can leave earlier edits and `hush.feature.*` metadata in output when Desktop continues after errors.
  Evidence: `controls/MessengerControlsPatch.kt::controlPatch`; [Patcher 1.14.1 execution](https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/Patcher.kt#L82); [Desktop 1.17.0 export path](https://github.com/MorpheApp/morphe-desktop/blob/v1.17.0/src/main/kotlin/app/morphe/desktop/command/PatchCommand.kt#L801).
  Touches: `MessengerControlsPatch.kt`, `ControlHooks.kt`, `PluginGates.kt`, capability resource dependency/finalization and Kotlin integration tests.
  Acceptance: Preserve both People method IDs and discovery anchors, but alter a validated operand or return polarity in the second method. Assert both still resolve, then run with `--continue-on-error`: neither People method changes nor its capability marker survive; an unrelated valid control succeeds. Default failure produces no rebuilt APK. Prevalidate all selected methods; don't introduce a general patcher transaction framework.
  Complexity: M

- [ ] P1: Preserve the supported-build description in compatibility metadata.
  Why: Morphe's legacy constructor drops the description naming both supported codes, making variant selection less clear.
  Evidence: `MessengerTarget.kt`, the released MPP constructor call and [Compatibility.kt](https://github.com/MorpheApp/morphe-patcher/blob/v1.14.1/src/main/kotlin/app/morphe/patcher/patch/Compatibility.kt).
  Touches: `patches/src/main/kotlin/app/hushmessenger/patches/MessengerTarget.kt`; `SourceIndexTest.kt`.
  Acceptance: Explicitly use the primary constructor with `versionCodes = null`. Assert the loaded bundle retains a nonempty description naming 346013387 and 346013440. Inspect Desktop metadata and Manager's supported-target presentation in isolation. Keep one version-name target; 346013370 still fails before mutation. Don't mislabel extra arm64 codes under other ABIs.
  Complexity: S

- [ ] P1: Exercise real control discovery and optional-hook drift.
  Why: Existing tests construct resolved maps; they don't call `findControls`, and the changed-APK script only alters permission literals.
  Evidence: `controls/ControlHooks.kt`, `ExpandedControlsTest.kt`, `scripts/verify_changed_apk_failure.py`; [Cleaner compatibility checker](https://github.com/N01-r0/messenger-cleaner-lsposed/blob/main/scripts/check-messenger-compat.py).
  Touches: Kotlin resolver/contract tests and the existing supplied-APK verification script.
  Acceptance: Synthetic ClassDef cases cover valid, absent and duplicate anchors, changed registers/returns and plugin gates through the real resolver. Each selectable control and the full union resolve on both private APKs. Optional-hook mutation fails predictably; unrelated selections remain unchanged. Emit a local per-feature structural result with build code and hash, explicitly distinct from runtime success. Keep proprietary fixtures and raw DEX out of Git.
  Complexity: M

- [ ] P1: Keep settings usable in short windows and at maximum text size.
  Why: Fixed header/footer space can consume the remaining scroll area; the existing 320 by 800 test only checks child bounds.
  Evidence: `SettingsActivity.buildHeader`, `scrollPage`, `SettingsNavigationTest.largeTextOnANarrowScreenKeepsSwitchesWithinTheirRows`; [Android text guidance](https://support.google.com/accessibility/android/answer/12159181?hl=en-GB).
  Touches: `extensions/messenger/src/main/java/app/hushmessenger/extension/SettingsActivity.java`, `SettingsUi.java`, navigation tests and existing screenshots.
  Acceptance: First reproduce in the existing harness. Cover API 28 and 36, both themes/pages, 320 dp width, a 640 by 360 dp landscape window, default/200% text, system insets and keyboard-visible search. Assert a usable viewport of at least one 48 dp target and reachable first/last controls by actual scrolling. Reflow the header/reminder where needed; capture isolated native results. This is separate from the blocked host Messenger font patch.
  Complexity: M

### P2: Next

- [ ] P2: Show bubble eligibility separately from saved selection.
  Why: The runtime disables bubbles below API 30 while settings can display and count them as enabled.
  Evidence: `Settings.enableBubbles`, `SettingsActivity.controlRow/updateSetup`, API 28 coverage in `SettingsTest.java`.
  Touches: `Settings.java`, `SettingsActivity.java` and Android tests.
  Acceptance: On API 28/29, an installed, saved-on bubble control explains Android 11 eligibility and isn't counted as effective. Preserve the saved choice. On API 30+, selection and pause behave consistently; help explains Android bubble settings and applicable notification permissions. Don't claim live bubble routing is verified by this UI fix.
  Complexity: S

- [ ] P2: Synchronize resumed settings and preserve search editing state.
  Why: A surviving activity binds old preference values, and recreation restores search text without its selection/focus.
  Evidence: `SettingsActivity.controlRow/onSaveInstanceState`; [Android view-state guidance](https://developer.android.com/topic/libraries/architecture/views/activity-lifecycle-views).
  Touches: `SettingsActivity.java`, `SettingsNavigationTest.java`.
  Acceptance: Create activities A/B, change a control and pause in B, resume A, and verify switches, counts and runtime gates agree without feedback loops. Recreate with a selected substring and restore cursor/selection plus appropriate focus. Switching to App must not leave hidden search focused. Existing keys, page/category and scroll restoration survive.
  Complexity: M

- [ ] P2: Check native compatibility and the real preflight parsing boundary.
  Why: Certificate checks don't compare device ABI/page size; tests bypass `read_apk` parsing.
  Evidence: `scripts/check_install.py::read_apk/check/main`, `scripts/tests/test_check_install.py`; [Android native requirements](https://developer.android.com/guide/practices/page-sizes). Both stock/rebuilt fixtures have 13 compressed arm64 libraries with 16384-byte minimum segment alignment.
  Touches: The preflight script, Python tests and its README usage.
  Acceptance: Report APK ABI, device ABI/API/page size and alignment without installation. An optional verified stock-APK input must match package/version code; compare library names, decompressed hashes and ELF headers with the rebuilt candidate. Without that baseline, report preservation as unchecked. Exclude compressed ZIP offsets from direct-mapping alignment checks. Synthetic tool outputs cover malformed fields, permissions, signer SDK arguments, timeouts and CLI exits. Unsupported ABI or unreadable required evidence returns nonzero; valid fixtures pass. Don't describe this as a startup fix.
  Complexity: M

- [ ] P2: Verify settings accessibility through nodes and actions.
  Why: Labels and minimum bounds alone don't establish page, selection or focus semantics.
  Evidence: `SettingsActivity.showPage/filterControls`, `SettingsNavigationTest.java`; [Android accessibility principles](https://developer.android.com/guide/topics/ui/accessibility/views/principles-views).
  Touches: `SettingsActivity.java`, `SettingsUi.java`, Android semantic tests.
  Acceptance: Controls/App expose pane and selected-tab state; each row has one actionable switch with checked state. Search/filter changes announce useful results without duplicate speech. Focus stays on visible content through navigation/recreation. Verify the tree and actions on API 28/36, then speech in an isolated session. Keep the blocked host chat-list TalkBack item separate.
  Complexity: M

- [ ] P2: Add a private setup summary and explain account scope.
  Why: App shows extension version/count only; reports need exact host context, and package-wide choices aren't explained.
  Evidence: `SettingsActivity.buildApp`, `Settings.initialize`, [issue 1](https://github.com/SysAdminDoc/HushMessenger/issues/1), [Beeper report guidance](https://help.beeper.com/en_US/troubleshooting/common-issues-and-how-to-report-bugs).
  Touches: `SettingsActivity.java`, Android tests and settings help.
  Acceptance: An explicit Copy setup action copies extension version, host package/version code, Android API, installed/selected flags and pause state. It excludes account IDs, contacts, messages, device serials, tokens and recovery material; nothing is sent. Explain that choices apply to every Messenger account within this installation. Verify no-control, partial-selection and paused states. Extend the existing diagnostics direction without adding telemetry or chat export.
  Complexity: S

- [ ] P2: Generate a public catalog and check release metadata locally.
  Why: The source has no `patches-list.json`; its current source-index test checks only timestamp syntax.
  Evidence: `SourceIndexTest.kt`, `patches-bundle.json`, [Morphe catalog task](https://github.com/MorpheApp/morphe-patches/blob/main/patches/build.gradle.kts), [catalog consumers](https://github.com/nvbangg/awesome-morphe/blob/main/CONTRIBUTING.md).
  Touches: `patches/build.gradle.kts`, local catalog generator, source-index tests and README catalog link.
  Acceptance: After the description fix, generate the compatible catalog from the built MPP. Check patch names, categories, defaults, dependencies and exact-build description against the loaded bundle; check source/release version, artifact name and hash together. Assert the 20 control keys match manifest capabilities and extension definitions. A changed key/version/hash fails the local check. Keep one version-name target, avoid fake ABI mappings, and add no hosted build workflow. This extends existing distribution work with metadata consistency.
  Complexity: M

- [ ] P2: Decouple localized labels from identifiers and support RTL settings.
  Why: English page/category text drives state, while physical left margins and hardcoded counts impede localization.
  Evidence: `SettingsActivity.CONTROLS/showPage/filterControls`; [Android pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales).
  Touches: `SettingsActivity.java`, `SettingsUi.java`, a small extension text catalog and Android tests.
  Acceptance: Stable IDs and existing preference keys survive locale changes and upgrades. English fallback, expanded pseudo text and RTL pseudo text preserve search/filter behavior, plural messages, start/end spacing and accessible names. Ensure pseudo text is actually transformed; locale switching alone doesn't translate Java literals. Keep text independent of host resource IDs unless namespaced merging is proven on both APKs. Reuse the short-window matrix.
  Complexity: M

### P3: Later

- [ ] P3: Report preference durability failures without blocking the UI.
  Why: The UI says choices are saved after `apply()`, which cannot report failed disk writes; data loss hasn't been reproduced.
  Evidence: `SettingsActivity.controlRow/updateSetup`; [SharedPreferences.Editor contract](https://developer.android.com/reference/android/content/SharedPreferences.Editor).
  Touches: `Settings.java`, `SettingsActivity.java`, preference fault-injection tests.
  Acceptance: Inject a failed write and establish the visible/restart behavior first. If adding durable-save feedback, serialize writes off the UI thread, preserve last-choice ordering during rapid toggles, distinguish pending/saved/failed status and offer immediate retry feedback. Verify failure, process recreation and successful retry without changing host account data or showing confirmation dialogs. Don't introduce a new storage framework without a demonstrated need.
  Complexity: M
