# Changelog

## Unreleased

- Restore screens on re-signed builds. Messenger checks the signing certificate of its own package against Meta's certificate table, and a re-signed build fails that check silently, leaving some screens blank. This always-on patch answers Messenger's signer lookup with the original Meta certificate for Messenger's own package only; other packages still get the system's real answer. Uses the same approach that fixed the equivalent problem in Hushfacebook. Hook count now 72 across 27 patches. Verified on S25: a 27-patch build signed with the Manager key launches straight to the signed-in chat list in dark theme, resolving the blank NeueNuxActivity that affected every prior re-signed build.
- Kept unsent messages survive a restart. When Keep unsent messages is on and a sender removes a message, the preserved message shows "[unsent]" before its text and stays visible after a delta sync. The keep_unsent hook extracts the message ID from the revoke intent and records it; an unsent_indicator hook on the message text wrapper prepends the label; a delta_unsent hook on the is_unsent getter suppresses the flag for recorded IDs so the server's unsent state can't hide them. Hook count now 72 across 27 patches.
- Open HushMessenger settings from the Menu tab on both layouts. The folder grid path hooks the settings folder item builder (HFb.Ax1) and the grid binder (TxV.CAo). For accounts where Messenger shows the plain list instead of folders, a third hook on the drawer items setter (Txc.A0I) clones an existing list entry and relabels it. The bind hook detects the "HushMessenger" label and attaches the click listener in both cases. Hook count now 72 across 27 patches.
- Fixed pre-existing test failures. Updated control count assertions from 20 to 24 across six test classes after four controls (use_system_emoji, allow_screenshot, hide_read_receipts, keep_unsent) were added without matching test updates. Fixed setup summary assertions for the new last_active field in copy_setup output.
- Fixed the debug dumper pre-scan to match the real APK's instruction order. The scan assumed labels came before their getter calls, but Messenger 580's KFp.A02 puts the 0-param KKn getter before the label string. Changed to look-behind and added KKn-to-K1Y name resolution so Keep unsent messages applies without --continue-on-error. Both CompatReport.java and the Kotlin production code use the corrected pattern.
- Dry-run compatibility report (`scripts/CompatReport.java`). Point it at any Messenger APK to see package, version code, ABI and signer, plus PASS/FAIL for all 27 patches with the failing contract. Exits non-zero on any failure, never modifies the APK. Requires JDK 21+ and the smali-dexlib2 and guava JARs.
- Added arm64 variant `346013442` (213-240dpi) to the supported build list. Downloaded from APKPure, verified with the dry-run compatibility report: all 26 patches pass with 67 hooks, same Meta signer, same DEX sites and manifest structure. The other six APKPure variants for this version are armeabi-v7a. Variant `346013445` has a different Redex obfuscation mapping and is not compatible.
- Fixed hook discovery for the AI search and keep-unsent controls. Both used exact string matching (`"SearchAiagentImplementationsKillSwitch"`, `"ACTION_REVOKE_MESSAGE"`) but the APK carries fully qualified names. Switched to substring matching in both the Kotlin production code and the Java compat tool. Unit test fixtures updated to use the real APK strings.
- Three new privacy controls (off by default, under a new Privacy group):
  - Allow screenshots: removes FLAG_SECURE and disables screenshot detection in vanish mode and E2EE chats (hooks SecureWindowUtils and ScreenshotContentObserver).
  - Hide read receipts: blocks the outgoing read-receipt runnable so senders don't see when you viewed their message (hooks ReadThreadManager).
  - Keep unsent messages: intercepts incoming message-revoke intents so messages other people remove stay visible locally (hooks ACTION_REVOKE_MESSAGE handler). No unsent indicator is shown; the message just stays. Delta sync after a restart may still apply the server's unsent state.
- Opt-in update check in the App tab (off by default). When on, opening settings fetches the latest GitHub release and shows whether a newer version is available, with a link to the release page. Makes no network request while off.
- Process audit: all 67 hooks confirmed to run in Messenger's main process. Non-default processes (:notification, :fbns, :fdidsync, :pretosproc, :quicksilver, :background_e2ee, :AsyncScriptingProcess, :bsod, :minigame) host services and receivers that don't overlap with any hooked class. The bubbles hook's callers (BubblesAppNotificationSettingsIntentHandler, 4Sc.A0G) are main-process intent builders. SettingsProvider initialization in the default process is sufficient.
- Each control row shows when its hook last ran. The timestamp records in memory whenever `enabled()` returns true, so it resets on restart. Copy setup includes last_active per control. This makes it obvious when a control's hook is working or silently inactive, like issue #2.
- Export and import control choices from the App tab. Export copies your switches and pause state to the clipboard; Import reads them back. Only known control keys and true/false values are accepted, and the import validates the format before applying. Useful when reinstalling with a different signing key wipes app data.
- New Use system emoji control (off by default, in Conversations). Hooks the FacebookEmojiTypefaceProviderImpl typeface method to return the system NotoColorEmoji.ttf instead of Messenger's built-in emoji font. The cached typeface loads once and falls back to stock rendering if the system file is missing.
- The Meta AI control now also hides the search AI entry. It suppresses the SearchAiagentImplementationsKillSwitch provider implementations (OpenAndSend and SendOnWelcomeMessage) so the AI agent doesn't load in search results. These use a different cache pattern than standard plugin gates, so they're handled with injectSwitch instead of plugin gate validation.
- DexScanner tooling and scanDex Gradle task for hook anchor discovery in stock APKs.
- Crash-loop safe mode: if Messenger exits abnormally three times within a minute after starting, all controls turn off automatically while keeping saved choices. The settings screen shows safe mode is active and why, and Resume clears it. Uses ApplicationExitInfo on API 30+ with a crash-marker fallback for older devices.
- The shortcuts XML is now found by scanning APK entries instead of expecting a fixed file name. Variant 346013442 stores it at res/eve.xml instead of res/los.xml; the patch now works with either.
- Other apps can no longer restart Messenger. Restart Messenger used to accept a request from any app on the phone, which could close Messenger at any moment, even during a call. The long-press shortcut and the App tab button still work.
- The search field now reads back what you typed with a screen reader. The selected filter keeps a visible focus ring, and switches that can't work on your Android version look unavailable.
- Quick toggles, including the theme switch, no longer stack up old messages. Pause now says "Changes paused" or "Changes resumed".
- The selected filter in the light theme is filled like it is in the dark theme. On Android 10 and newer the search cursor uses the app's blue, and the restart screen and its system bars follow your theme. The header now lines up with the cards.
- The title stays on one line at large text sizes on older Android versions, and very long pasted searches are trimmed.
- In the right-to-left test language, headings and descriptions now line up on the right instead of running into their counts.
- The README links the two APKMirror builds that work, since six look alike on that page. The Install beside Meta apps description no longer says signed builds don't open chats.
- Building the same source now gives the same bundle from any checkout. The license files used to carry whichever line endings Git wrote, so a fresh clone couldn't reproduce the published checksum.
- Release checks compare every README link to this project's own releases, and the permission failure check says when Morphe Desktop never started. Pass 163 local tests.

## 0.4.2 (2026-09-28)

- Hide People You May Know now also clears the suggestions on Messenger's Notifications tab and the block after the last chat. It takes the same path as Messenger's own Hide option, even on accounts where Meta turns that option off from its servers, and it never changes Messenger's settings. Pausing HushMessenger or turning the switch off brings the suggestions back. Checked on the S25: the Notifications tab lost its suggestions, they returned while paused, and chats and notifications still showed. The end-of-list block and the server override didn't apply to that account, so they're covered by build checks only.
- Pass 157 local tests. Both supported APKs apply all 21 patches, and two clean builds produce the same bundle.
- Explain where to find the settings after patching: long-press Messenger for Patch controls, or open HushMessenger settings from the app drawer.
## 0.4.1 (2026-09-27)

- Long-press Messenger for Patch controls or Restart Messenger. Keep the app-drawer settings entry and cover Messenger's alternate icons.
- Add Quick access and a restart button at the top of the App tab. Explain when inbox changes need a restart.
- Save pending choices before restarting the main process. A failed save leaves Messenger running and shows an error. Restarting doesn't clear app data.
- Preserve Messenger's direct-share target, all original resource values and existing hook behavior. Rebuild both supported APKs with all 21 patches and verify their signatures.
- Pass 153 local tests. Exercise the long-press menu and fresh-process restart in a temporary S25 test app, retain saved choices, and recapture both settings pages and themes. Remove the test app afterward.

Update the already patched S25 in place with its matching Morphe key. Both real launcher actions and the settings restart button pass, the signed-in chat screen returns in a fresh process, and 19 enabled controls survive. S22 remains stock. Fresh-install sign-in and encrypted-history recovery still need testing.

## 0.4.0 (2026-09-27)

- Pass 133 local tests and rebuild both exact APKs with all 21 patches. Verify all 57 runtime hook calls and preserve every stock class and native library. Both installed phone APKs remain stock; their account data is unchanged.
- Verify the published bundle checksum and all 21 entries through Morphe's remote lookup. Refresh the S25 source to v0.4.0. Source updates do not install a patched Messenger APK.

- Keep multi-digit counts and version numbers in their normal reading order in the right-to-left test language.
- Distinguish named and hidden dependencies in the generated catalog, including their transitive dependency graph.
- Clearly mark the standalone settings app as a UI preview and remove its app-drawer entry. Preview setup reports never claim active Messenger controls. Remove leftover previews from both test phones after confirming their Messenger APKs remain stock.

- Keep page and category state independent of translated labels. Add an English text catalog with expanded and right-to-left test languages, whole plural messages and locale-aware numbers. Honor Android app languages even when the host resource table falls back to English.

- Publish a catalog generated from the built patch bundle. Local checks reject changed control keys, metadata, release versions and checksums before publication.
- Add Copy setup to the App tab. It copies app versions and control states only when tapped, excludes account and chat data, and distinguishes saved choices from active controls while paused or unavailable.

## 0.3.1 (2026-09-27)

- Require both shared permissions to retain signature protection. Check their DEX sites before renaming the manifest permissions.
- Validate every method in a control before editing it, then publish its settings capability only after success. Reject changed browser parameters and plugin return constants.
- Keep both supported build codes visible in Morphe's compatibility description.
- Keep controls reachable in short windows with large text by moving the branding and reminder into the scroll area. Let navigation scroll when the keyboard leaves very little room. Preserve the normal portrait layout.
- Restore search selection and each page's scroll position, refresh switches when returning to settings, and keep focus on visible controls.
- Explain bubble availability on Android 9 and 10 without discarding the saved choice. Add accessible page names, correct right-to-left spacing and explain that choices apply to every account in the installation.
- Reject incomplete or conflicting certificate, permission and package records during the installation check. Include apps installed in other Android profiles.
- Check arm64 libraries, ELF page alignment and Android's native extraction rules. The optional `--stock-apk` argument verifies that a rebuild preserves the tested stock libraries.
- Return clear errors for missing inputs, tool timeouts, corrupt compressed native libraries and malformed changed-APK reports. Read wrapped dependency errors and keep temporary APK cleanup on failure.

- Pass 103 local tests. Android lint reports no errors and the same eight warnings as the baseline.
- Rebuild both supported APKs with all 21 patches, verify their v3 signatures and preserve every native library. Two clean bundle builds produce the same checksum.
- Exercise both settings pages and themes on an isolated S22 display. Check small windows, 200% text and keyboard-visible search on a headless Android 16 emulator.

## 0.3.0 (2026-09-27)

- Redesign settings with separate Controls and App tabs, compact grouped rows and category filters.
- Add a live enabled count and a clear paused state. Keep all patch selections and saved preferences.
- Match the new dark and light layouts with shared colors, rectangular switches and readable section headings.
- Keep appearance, version details and setup help together in the App tab.
- Preserve the selected tab, category, search and scroll position when changing themes. Keep 48 dp touch targets and adapt the header to narrow screens or larger text.
- Pass all 57 local tests and Android lint. Rebuild both supported APKs with all 21 patches and verify their v3 signatures. Compare both settings pages against the design references on an isolated S22 display.
- Verify the public download matches both clean builds and that Morphe Desktop fetches v0.3.0 with all 21 entries. Phone source refreshes remain queued while the S25 is disconnected and the S22's Manager session is active.

## 0.2.0 (2026-09-27)

- Replace the two-entry catalog with 21 selectable patches. Each feature applies independently and shares one settings extension.
- Add 13 optional controls: inbox ad filtering, People You May Know, friend request cards, growth prompts, Chat Moments, AI sticker tools, avatar stickers, inbox promotions, chat promotions, business reply suggestions, business typing suggestions, event prompts and the Reels badge.
- Extend the existing Meta AI control to the inbox toolbar button.
- Add settings search and show only installed controls. Saved choices survive removing and later adding a patch.
- Preserve stock behavior while switches are off or changes are paused. Check exact hook sets and the new plugin gates' disabled branches before patching.
- Add regression tests for separate selection, capability metadata, search and ad-filter return branches. Existing tests remain intact.
- Pass 26 Kotlin, 15 Android and 11 Python tests, Android lint, both full APK rebuilds and a separate single-control rebuild. Exercise settings search, persistence and themes on an isolated S25 display.
- Refresh the remote source on S22 and S25 and verify all 21 entries in each phone's catalog. Keep their installed Messenger apps intact.
- Keep the ad filter experimental. No affected-account ad row was available for a live removal check. Original-package startup remains unresolved.

## 0.1.0 (2026-09-27)

- Add seven optional controls adapted from existing Messenger patches: stories and notes, inbox tabs, Facebook shortcuts, Meta AI buttons, external web links, typing signals and bubble eligibility. All switches start off.
- Add a HushMessenger settings launcher entry with saved switches, a pause control and a light theme. Inbox changes take effect after reopening Messenger.
- Check the complete hook set on both supported 580 APKs. Changed hooks stop patching rather than silently skipping a feature.
- Verify the settings screen, light theme, pause and Messenger launch on an isolated S25 diagnostic copy. Stories, the Facebook toolbar shortcut and the Meta AI floating button passed before/after checks. The original-package startup failure remains unresolved.
- Pass 20 patch, nine Android settings and 11 certificate tests. Both exact APKs patch and sign successfully.
- Verify the v0.1.0 remote source and both patch entries on S22 and S25. Their stock Messenger installations and sign-ins stayed intact.
- Correct the remote index timestamp for Manager's local date-time parser. The bundle download and checksum are unchanged.
- Add bytecode and Android settings tests. Keep ad blocking and media transcoding unavailable until their current paths can be verified.

## 0.0.9 (2026-09-27)

- Add a read-only installation check that verifies APK certificates and reports installed-app or permission-owner conflicts before any device changes. It checks the phone's current signer set, including API-specific rotation and multiple signers, and refuses uncertain results.
- Cover the check with eleven Python tests and exercise it against the S25's installed apps, including a deliberately mismatched signing fixture.
- Confirm stock encrypted message delivery between two owned accounts on S22 and S25. The S22's stock browser switch passed HTTP and HTTPS comparisons; its original setting was restored. Re-signed Messenger still needs a working startup path.

## 0.0.8 (2026-09-27)

- Record the S25 test: a same-key patched Messenger installed, but its first-run screen stayed blank. An unchanged APK rebuilt and signed through the same tool did the same.
- Clarify the phone-data warning after reinstalling the original stock APK required a fresh sign-in. Chats, calls and notifications remain unverified.

## 0.0.7 (2026-09-27)

- Record the S22 stock Messenger controls for inbox suggestions, external links, notifications, chat heads and accessibility without changing account settings.
- Refine patch plans around the controls Messenger already provides, including checks that would justify a separate browser or suggestions patch.
- Rebuild the preview bundle and repeat the S22 off-device patch, signature and changed-APK checks.

## 0.0.6 (2026-09-27)

- Add the S25's Messenger 580 arm64 build to the exact compatibility list after comparing its APK with the S22 fixture.
- Verified that altered permission bytecode in either APK stops patching before an output is written.
- Left both phones' installed Messenger apps and local chat data untouched.

## 0.0.5 (2026-09-27)

- Add a repeatable changed-APK check that confirms Morphe stops before rebuilding when permission bytecode differs from the tested Messenger build.
- Ignore local signing keys and Python cache files so they cannot be staged by accident.
- Explain that Manager and Desktop currently load patch bundles without verifying the source index's detached-signature URL.
- Keep device-dependent patch work in a blocked tracker until an isolated signed-in arm64 session can verify it.

## 0.0.4 (2026-09-27)

- Put the tested APK and phone-data warning before the preview setup steps.
- Make the README easier to use on a phone, including a copyable stock APK checksum and recovery help.
- Move the support link below the setup and verification details.
- Give patch failures the exact Messenger build needed to retry with an unmodified APK.
- Shorten the patch and source descriptions and cover the recovery message in ten unit tests.
- Check both Manager 1.32.0 source methods and the published source in Morphe Desktop 1.17.0.

## 0.0.3 (2026-09-27)

- Reject Messenger builds whose shared-permission manifest counts, component owners or active DEX instruction sites differ from the checked 580 APK.
- Add regression tests for missing or reassigned guards, duplicate declarations and changed DEX sites.
- Use the smali revision requested by Morphe Patcher 1.14.1.
- Give the patch bundle a fixed release timestamp so clean builds have the same checksum.
- Lock build dependencies and verify their artifact hashes, including the Morphe build plugin.
- Explain how to load a preview source in Morphe Manager and Desktop.

## 0.0.2 (2026-09-27)

- Published the Messenger patch source with a README hero, icon and social preview in the Hush project style.
- Added a Morphe source index and a preview bundle for the exact Messenger 580 arm64 target.
- Rebuilt the bundle locally, applied it to a stock APK copy and verified the signed output off-device.
- Documented the signing-key requirement and the remaining phone checks.

## 0.0.1 (2026-09-27)

- Started a separate local Morphe patch source for Messenger `580.0.0.49.91` (arm64, version code `346013387`).
- Added a shared-permission rename patch for installation beside Meta apps.
- Added the project research, roadmap, build notes and GPL attribution.
