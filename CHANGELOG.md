# Changelog

## 0.1.0 (2026-09-27)

- Add seven optional controls adapted from existing Messenger patches: stories and notes, inbox tabs, Facebook shortcuts, Meta AI buttons, external web links, typing signals and bubble eligibility. All switches start off.
- Add a HushMessenger settings launcher entry with saved switches, a pause control and a light theme. Inbox changes take effect after reopening Messenger.
- Check the complete hook set on both supported 580 APKs. Changed hooks stop patching rather than silently skipping a feature.
- Verify the settings screen, light theme, pause and Messenger launch on an isolated S25 diagnostic copy. Stories, the Facebook toolbar shortcut and the Meta AI floating button passed before/after checks. The original-package startup failure remains unresolved.
- Pass 20 patch, nine Android settings and 11 certificate tests. Both exact APKs patch and sign successfully.
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
