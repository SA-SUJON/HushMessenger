# HushMessenger blocked roadmap

These items need the named external test session or signed-in browser access. They return to ROADMAP.md when that access exists.

## S22 stock baseline, 2026-09-27

The attached S22 runs stock Messenger 580.0.0.49.91, version code 346013387. Its signed-in app was inspected on a separate Android virtual display, leaving the phone's main display, installed package and account settings untouched. Private captures stay off-repository. This establishes the stock UI only; it does not validate a re-signed APK, notification delivery, calls or encrypted chat recovery.

- The Chats screen shows an `Ask Meta AI or search` field, a stories/notes tray and a `People you may know` section. That section's menu offers `See more suggestions` and `Hide suggestions`. No sponsored chat row was visible in the captured viewport.
- **Me > Photos & media** has `Open links in external browser`, currently off. Its description says links shared in messages open externally. It also has `Save photos and videos`, currently off. Neither switch was changed, and no message link or safety warning was opened.
- **Me > Notifications & sounds** exposes switches for community activity and suggestions, channel invites, message reminders, new friend chats, notes and instants. This is category control; a selected-contact allowlist was not established. No delivery or reconnect test was run.
- **Me** shows Chat heads off. **Me > Accessibility** has Reduce motion set to System and Color filter off. These screens do not establish TalkBack speech, system font scaling, HD media behavior or Android bubble behavior.

## Existing priorities

- [ ] P0: Validate the first patch against a signed installation.
  Why: A completed off-device patch run does not establish that Messenger launches or works after re-signing.
  Evidence: `RESEARCH.md` target APK and signer findings; `patches/src/main/kotlin/app/hushmessenger/patches/coexist/InstallBesideMetaAppsPatch.kt`. The S22 and S25's exact 580 APKs passed the 26 plus three manifest owner checks, six active DEX site checks, off-device patching and APK v3 signing verification on 2026-09-27. Altered copies of both APKs stopped before output. Stock and patched Messenger both crashed in the x86 emulator's arm64 native bridge before opening chats. The available native arm64 image cannot boot on this x86 host.
  Touches: `scripts/`, a private APK fixture, patch result parsing, manifest comparison, device test notes.
  Acceptance: The exact stock 580 APK applies cleanly and fails closed if its expected 26 plus three manifest mentions or six active permission loads change; its two declarations and every request and guard use the new names. A signed build opens to chats on an isolated test session. Check muted DM and group notifications, read-while-open behavior, WiFi and cellular reconnect, calls, Facebook links, secure-storage recovery and same-key update/rollback before any uninstall. Raw old strings may remain in unused `STRIP_FAST` string pools.
  Next check: Keep both private APKs out of Git. The unit tests and `scripts/verify_changed_apk_failure.py` reject changed DEX sites before output. Use a backed-up signed-in arm64 test session for runtime checks. The attached S22 and S25 both have stock Messenger and local data that must stay intact until that session exists.
  Complexity: L
  Blocked: An isolated, backed-up, signed-in arm64 test session is required for chats, notifications, calls, recovery and rollback. Both attached phones support one user and hold stock Messenger; the x86 emulator crashes on the unmodified stock APK.

- [ ] P0: Restore trusted cross-app behavior for a re-signed Messenger.
  Why: Changing permission names does not satisfy Messenger and Facebook's signer checks by itself.
  Evidence: `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` signature and provider inspection; [De-Vanced 96](https://github.com/RookieEnough/De-Vanced/issues/96), [Meridian 24](https://github.com/meridianfresco/morphe-meta-patches/issues/24), [Android signature permission rules](https://developer.android.com/guide/topics/manifest/permission-element).
  Touches: `patches/src/main/kotlin/app/hushmessenger/patches/coexist/`, `scripts/`, companion Hushfacebook compatibility check.
  Acceptance: Stock Facebook plus patched Messenger and a same-key patched Facebook/Messenger pair install and pass login, account switch, notification deduplication and call checks. On API 28+, real partner signer identity, rotation/multiple signers, package visibility and guarded provider access are checked. A different-key pair fails with a clear diagnostic before install; package name alone never grants trust.
  Complexity: XL
  Blocked: A signed-in arm64 session and same-key Hushfacebook/Messenger pair are required to verify login, account switching, provider access and calls without risking either phone's stock data.

- [ ] P1: Trace and remove the actual chat-list ad row on Messenger 580.
  Why: The earlier inbox ad loader is gone; a patch that skips a missing fingerprint can claim success while ads remain.
  Evidence: [De-Vanced inbox fix](https://github.com/RookieEnough/De-Vanced/commit/de0a110ac8ce36423144c841a0f1ec6e05383356), [issue 57](https://github.com/RookieEnough/De-Vanced/issues/57), [Messenger Cleaner's typed-item processor](https://github.com/N01-r0/messenger-cleaner-lsposed/blob/main/app/src/main/java/dev/or10n/messengercleaner/patches/InboxAdsPatch.kt), `RESEARCH.md` APK leads.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/inbox/` hooks, fixture checks.
  Acceptance: A captured affected account shows the traced ad row absent after patching; ordinary business conversations and safety notices remain visible. The actual 580 item producer is named in the patch notes, and a missing fingerprint fails rather than skipping.
  Complexity: L
  Blocked: An affected signed-in account must show the actual ad row before its producer can be traced or removal verified. The older loader is absent from 580.

- [ ] P1: Hide story ads without losing regular Montage stories.
  Why: The 580 APK retains Montage ad models and query names, while Facebook's story patch targets a different viewer.
  Evidence: `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` Montage anchors; [Rushi Messenger catalog](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/stories/` hook.
  Acceptance: A signed-in account can view ordinary stories, but a reproduced sponsored story no longer appears; playback and navigation still work.
  Complexity: L
  Blocked: A signed-in arm64 account showing a sponsored Montage story is needed to trace the viewer and verify normal story playback.

- [ ] P1: Add user controls for inbox suggestions and Meta AI entry points.
  Why: Both surfaces occupy chat-list space. The S22's stock 580 build already offers `Hide suggestions` for the People you may know section, while `Ask Meta AI or search` remains a separate entry point.
  Evidence: S22 stock baseline above; [ReVanced Messenger patch API](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/api/patches.api?ref_type=heads), [Messenger Cleaner](https://github.com/N01-r0/messenger-cleaner-lsposed/blob/main/README.md), [People You May Know request](https://github.com/RookieEnough/De-Vanced/issues/38), `RESEARCH.md` supplier leads.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/inbox/` and `settings/`; Android 9 compatible extension code.
  Acceptance: Check whether stock `Hide suggestions` persists and covers every suggestion placement. Add a suggestions patch only for a demonstrated gap. A separate opt-in Meta AI UI control must preserve ordinary search, chats, tabs, scam warnings and notifications. Capture each affected surface before and after; never claim a UI hide blocks Meta data processing.
  Complexity: L
  Blocked: The S22 shows both surfaces and the stock suggestions menu. Persistence after `Hide suggestions`, other suggestion placements and a Meta AI UI change still need a controlled session that preserves search, safety notices and regular chats.

- [ ] P1: Open message links in the chosen external browser.
  Why: Users request direct browser opening. The S22's stock 580 build has an `Open links in external browser` switch, so a patch needs a reproduced failure after that setting is enabled.
  Evidence: S22 stock Photos & media baseline above; [De-Vanced browser PR](https://github.com/RookieEnough/De-Vanced/pull/84), [fingerprint failure](https://github.com/RookieEnough/De-Vanced/issues/97), [Messenger link safety](https://engineering.fb.com/2026/03/09/security/how-advanced-browsing-protection-works-in-messenger/).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/links/` hook and settings control.
  Acceptance: On a backed-up test account, compare HTTP and HTTPS message links with the stock switch off and on, including encrypted chats, internal Messenger routes and unsafe-link warnings. If stock satisfies the behavior, close this patch idea with the result. Otherwise, trace the failing route and make the opt-in patch open the chosen browser while preserving those routes and warnings.
  Complexity: M
  Blocked: The stock preference is confirmed on S22, but its effect on message links and safety interstitials still needs a controlled signed-in session, including encrypted chats.

- [ ] P2: Check update prompts, chat heads and media quality on the exact target.
  Why: Existing catalogs list these features, but the current target and Android 9 compatibility have not been established.
  Evidence: S22 stock baseline above (Chat heads and Photos & media); [Rushi Messenger catalog](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md), [chat-head request](https://github.com/RookieEnough/De-Vanced/issues/91), [native-bubble request](https://github.com/RookieEnough/De-Vanced/issues/47), `RESEARCH.md` version constraint.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/updates/`, `chatheads/`, `media/`.
  Acceptance: Separate opt-in patches pass fixture checks and user-visible tests. Distinguish Play Store update offers from server-required app updates without spoofing a maximum version code. Compare identical media files against stock HD sending, already offered by Messenger. Test chat heads and native bubbles as distinct modes and respect Android's permission flow.
  Complexity: L
  Blocked: A signed-in arm64 test session is needed to compare stock controls and identical media, and to exercise update, bubble and chat-head flows.

- [ ] P2: Prepare repeatable local distribution and diagnostics.
  Why: The v0.0.4 preview has a versioned source index, reproducible bundle, exact-build mismatch guidance and a recovery section. It still needs device-tested recovery and wider-language checks.
  Evidence: [Morphe template](https://github.com/MorpheApp/morphe-patches-template), [ReVanced Manager patching flow](https://github.com/ReVanced/revanced-manager/blob/main/docs/2_1_patching.md), [Android developer verification FAQ](https://developer.android.com/developer-verification/guides/faq).
  Touches: `scripts/`, `README.md`, `CHANGELOG.md`, `patches/build.gradle.kts`, bundle metadata.
  Acceptance: A clean local build produces `patches-<version>.mpp`, a mismatch gives package/version/hash diagnostics, and release instructions cover same-key signing, secure-storage backup and rollback, plus an English and one non-English device check. Explain Android developer verification accurately for direct sideloading before the broader 2027 rollout; do not claim the 2026-09-30 store rollout blocks this channel.
  Complexity: M
  Blocked: Clean builds and mismatch diagnostics pass, but same-key recovery and one non-English device check require an isolated, backed-up, signed-in arm64 session.

## Research-Driven Additions

- [ ] P0: Make the preview bundle discoverable in Morphe.
  Why: Morphe Desktop's default stable source lookup rejects the repository because v0.0.4 is only a prerelease; users can fail before choosing a patch.
  Evidence: [Morphe Desktop source docs](https://github.com/MorpheApp/morphe-desktop/blob/main/docs/documentation.md), [Manager source docs](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md), local 2026-09-27 stable and `--prerelease` lookup results in `RESEARCH.md`. A clean Manager 1.32.0 emulator profile imported v0.0.4 by remote URL and local `.mpp`, refreshed the remote source with preview enabled and listed the patch. Manager displayed duplicate HushMessenger cards when both methods were used, so the README now recommends one. Morphe Desktop fetched v0.0.4 with `--prerelease`. The README's add-source deep link still needs a separate device check.
  Touches: `README.md`, `patches-bundle.json`, release metadata and source-import instructions.
  Acceptance: Confirm the add-source deep link on a clean Android device. After signed runtime validation, publish a stable release discoverable without a preview flag and point its index to the tested bundle.
  Complexity: S
  Blocked: A stable release depends on signed runtime validation. The add-source deep link still needs an isolated Android UI check; the prior emulator URL launch was blocked by command policy.

- [ ] P1: Correct chat-list speech for TalkBack when reproduced on 580.
  Why: Android comments dated 2026-04-25 and 2026-04-27 say Messenger speaks chat rows as numbers or letters while in-chat reading works; the inbox is the app's primary workflow.
  Evidence: [AppleVis report](https://www.applevis.com/forum/assistive-technology/meta-has-changed-accessibility-experience-messenger-again-web-time), `MessengerTarget.kt` exact build gate.
  Touches: a new `patches/src/main/kotlin/app/hushmessenger/patches/accessibility/` hook, fixture checks, optional settings label.
  Acceptance: Capture the stock 580 accessibility tree and TalkBack speech for at least one chat row. If broken, patched rows announce sender, unread state and any preview the user has allowed, without duplicate speech; swipe actions and encrypted chats still work. If stock 580 is already correct, record that fixture result and avoid a patch.
  Complexity: L
  Blocked: The S22's in-app Accessibility page exposes Reduce motion and Color filter, but the stock chat row's accessibility tree and TalkBack speech still need capture before a hook is justified.

- [ ] P1: Respect Android's system font size in Messenger text.
  Why: A Samsung user report describes scaling trouble, and the older 580 fixture has a Messenger typeface resolver that may override system sizing.
  Evidence: [Samsung font report](https://www.reddit.com/r/SamsungGalaxyS26U/comments/1vmq86m/question_about_font_size_on_samsung_galaxy_s26/), `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` typeface anchor.
  Touches: a new `patches/src/main/kotlin/app/hushmessenger/patches/accessibility/` hook and Android 9 compatible settings if needed.
  Acceptance: Compare stock and patched 580 at default and largest system font/display sizes; chat list, composer, thread and settings text scale without clipped controls. If stock already scales, document that finding and omit the hook.
  Complexity: M
  Blocked: Stock and patched Messenger need comparison at default and largest font/display sizes on an isolated signed-in arm64 session.

- [ ] P2: Set the prepared GitHub social preview.
  Why: `assets/github-social-preview.png` is committed, but the repository still uses GitHub's generated link preview, so shared links do not show the HushMessenger artwork.
  Evidence: `gh repo view` reported `usesCustomOpenGraphImage: false` on 2026-09-27. [GitHub's API discussion](https://github.com/orgs/community/discussions/172072) confirms no supported REST or GraphQL upload for this setting.
  Touches: GitHub repository Settings > Social preview; no new artwork is needed.
  Acceptance: In an isolated signed-in browser session, upload the prepared image, then verify `usesCustomOpenGraphImage: true` and that the returned image URL resolves. Do not operate the user's active display.
  Complexity: S
  Blocked: GitHub exposes no supported API for this upload. It needs an isolated signed-in browser session; operating the user's active display is prohibited.

- [ ] P2: Add optional tracking cleanup for outbound web links.
  Why: MessengerEx exposes an independent link-privacy option; external-browser choice alone does not remove tracking wrappers or parameters.
  Evidence: [MessengerEx URL helper](https://github.com/C10udburst/MessengerEx/blob/master/app/src/main/java/io/github/cloudburst/messengerex/Utils.kt), [Messenger link-click protection](https://engineering.fb.com/2026/03/09/security/how-advanced-browsing-protection-works-in-messenger/).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/links/` logic and a separate settings switch.
  Acceptance: An opt-in control unwraps only validated HTTP(S) tracking redirects and strips an explicit tested parameter set; signed links and functional query parameters stay intact. Malicious-link warnings, internal Messenger routes and stock link opening still work.
  Complexity: M
  Blocked: A signed-in 580 session is needed to trace outbound links and verify that safety interstitials, encrypted chats and internal routes survive cleaning.

- [ ] P2: Add an independent stories and notes tray switch.
  Why: A De-Vanced contribution reports unwanted people appearing in this tray, which is separate from sponsored stories and inbox suggestions.
  Evidence: [De-Vanced PR 53](https://github.com/RookieEnough/De-Vanced/pull/53).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/stories/` UI hook and settings switch.
  Acceptance: On a signed-in 580 account with a visible tray, the opt-in switch hides only that tray; opening a known contact's story through other available routes and normal chat navigation still work.
  Complexity: M
  Blocked: The S22's Chats tray is visible. Its exact UI hook and alternate story routes still need a controlled before/after check.

- [ ] P2: Add an optional Facebook shortcut switch.
  Why: Messenger's Facebook entry points add navigation clutter, but removing them must not interfere with account sharing or cross-app trust.
  Evidence: [De-Vanced PR 84](https://github.com/RookieEnough/De-Vanced/pull/84), existing cross-app trust item above.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/navigation/` UI hook and settings switch.
  Acceptance: All Facebook shortcut entry points found in the exact 580 UI disappear when enabled; login, account switch, provider access and direct Facebook deep links still pass the cross-app checks.
  Complexity: M
  Blocked: The exact 580 entry points and cross-app behavior need a signed-in arm64 test session before removing navigation hooks.

- [ ] P2: Hide joined community chats from the main inbox on request.
  Why: A user report describes joined community chats filling the inbox; these are real conversations and need a reversible view choice, not the suggestion filter above.
  Evidence: [community chat complaint](https://www.reddit.com/r/facebookmessenger/comments/1fqdruk/my_messenger_app_updated_and_it_now_displays_all/), `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` supplier list.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/inbox/` row classification and settings switch.
  Acceptance: A signed-in account with joined community chats can hide those rows from the main inbox through an opt-in switch without losing message delivery, unread counts, search access or a route back to each chat; suggested community promotions remain governed by the existing inbox controls.
  Complexity: L
  Blocked: A signed-in account with joined community chats is needed to trace their row type and verify delivery, unread counts and a route back.

- [ ] P2: Check selected-chat notification exceptions before adding an allowlist.
  Why: A MessengerPro request asks for silence except chosen contacts, but Android channels and stock Messenger mute controls may already cover part of this need.
  Evidence: S22 stock Notifications & sounds baseline above; [MessengerPro issue 41](https://github.com/Mino260806/MessengerPro/issues/41), [Android conversation notification guidance](https://developer.android.com/develop/ui/views/notifications/conversations).
  Touches: notification mapping in a new `patches/src/main/kotlin/app/hushmessenger/patches/notifications/` hook and optional settings.
  Acceptance: Document stock 580 controls first. If needed, an opt-in allowlist silences nonselected message alerts while preserving delivery, selected-chat alerts and calls; test DM/group, muted thread, background, WiFi and cellular reconnect cases.
  Complexity: L
  Blocked: The S22's top-level category switches are documented. A controlled session with selected DM and group threads is still needed to inspect per-chat controls and test alert delivery across reconnects.

- [ ] P3: Add story saving only after locating the 580 media path.
  Why: A 2026-09-14 request asks for a save control, but an older implementation does not establish the 580 story viewer or media permissions.
  Evidence: [Rushi issue 1004](https://github.com/rushiranpise/morphe-patches/issues/1004), `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` Montage anchors.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/stories/` UI and save hook, Android 9 compatible storage handling.
  Acceptance: On an owned or permitted test story, an opt-in save control writes one playable file to a user-selected location, reports errors in-app, and leaves story playback and normal privacy controls intact.
  Complexity: L
  Blocked: An owned or permitted test story on a signed-in arm64 session is required to trace the media path and verify saved playback.

- [ ] P3: Suppress typing signals only if encrypted chats remain correct.
  Why: De-Vanced has a typing hook, while a MessengerPro report describes E2EE failures in a comparable modification.
  Evidence: [De-Vanced typing hook](https://github.com/RookieEnough/De-Vanced/blob/main/patches/src/main/kotlin/app/morphe/patches/messenger/inputfield/DisableTypingIndicatorPatch.kt), [MessengerPro issue 70](https://github.com/Mino260806/MessengerPro/issues/70).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/input/` hook and separate settings switch.
  Acceptance: With an opt-in switch, a second test account sees no typing event while messages still send and decrypt in one-to-one and group encrypted chats; the default retains stock behavior.
  Complexity: M
  Blocked: Two signed-in test accounts on arm64 devices are required to observe typing events and verify one-to-one and group E2EE delivery.
