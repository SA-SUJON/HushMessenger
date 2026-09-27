# HushMessenger roadmap

Only unfinished work is listed. Each item needs a stock APK fixture and a visible behavior check before it can be marked complete.

## Research-driven additions

- [ ] P0: Validate the first patch against a signed installation.
  Why: A completed off-device patch run does not establish that Messenger launches or works after re-signing.
  Evidence: `RESEARCH.md` target APK and signer findings; `patches/src/main/kotlin/app/hushmessenger/patches/coexist/InstallBesideMetaAppsPatch.kt`.
  Touches: `scripts/`, a private APK fixture, patch result parsing, manifest comparison, device test notes.
  Acceptance: The exact stock 580 APK applies cleanly, its two declarations and every request and guard use the new names, modified methods load only the renamed literals, and a signed build opens to chats with notifications, calls and Facebook links checked on an isolated test session. Raw old strings may remain in unused `STRIP_FAST` string pools.
  Complexity: L

- [ ] P0: Restore trusted cross-app behavior for a re-signed Messenger.
  Why: Changing permission names does not satisfy Messenger and Facebook's signer checks by itself.
  Evidence: `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` signature and provider inspection; [De-Vanced 96](https://github.com/RookieEnough/De-Vanced/issues/96), [Meridian 24](https://github.com/meridianfresco/morphe-meta-patches/issues/24), [Android signature permission rules](https://developer.android.com/guide/topics/manifest/permission-element).
  Touches: `patches/src/main/kotlin/app/hushmessenger/patches/coexist/`, `scripts/`, companion Hushfacebook compatibility check.
  Acceptance: Stock Facebook plus patched Messenger and a same-key patched Facebook/Messenger pair install and pass login, account switch, notification deduplication and call checks. A different-key pair fails with a clear diagnostic before install.
  Complexity: XL

- [ ] P1: Trace and remove the actual chat-list ad row on Messenger 580.
  Why: The earlier inbox ad loader is gone; a patch that skips a missing fingerprint can claim success while ads remain.
  Evidence: [De-Vanced inbox fix](https://github.com/RookieEnough/De-Vanced/commit/de0a110ac8ce36423144c841a0f1ec6e05383356), [issue 57](https://github.com/RookieEnough/De-Vanced/issues/57), `RESEARCH.md` APK leads.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/inbox/` hooks, fixture checks.
  Acceptance: A captured affected account shows the ad row absent after patching, ordinary business conversations remain visible, and a missing 580 fingerprint fails the patch rather than skipping it.
  Complexity: L

- [ ] P1: Hide story ads without losing regular Montage stories.
  Why: The 580 APK retains Montage ad models and query names, while Facebook's story patch targets a different viewer.
  Evidence: `C:/Obsidian/Notes/Research/Messenger Scoping 2026-09-26.md` Montage anchors; [Rushi Messenger catalog](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/stories/` hook.
  Acceptance: A signed-in account can view ordinary stories, but a reproduced sponsored story no longer appears; playback and navigation still work.
  Complexity: L

- [ ] P1: Add user controls for inbox suggestions and Meta AI entry points.
  Why: Both surfaces occupy chat-list space and existing sources show multiple insertion points.
  Evidence: [ReVanced Messenger patch API](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/api/patches.api?ref_type=heads), [Messenger Cleaner](https://github.com/N01-r0/messenger-cleaner-lsposed/blob/main/README.md), [People You May Know request](https://github.com/RookieEnough/De-Vanced/issues/38), `RESEARCH.md` supplier leads.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/inbox/` and `settings/`; Android 9 compatible extension code.
  Acceptance: Each control can be switched independently; regular chats, search, tabs and notifications remain usable. Screenshots show each surface before and after.
  Complexity: L

- [ ] P1: Open message links in the chosen external browser.
  Why: Both De-Vanced and Rushi offer this as a repeated Messenger request.
  Evidence: [De-Vanced release change](https://github.com/RookieEnough/De-Vanced/commit/397c7202069ebcb6a11c4841bc10c513d78b3fbc), [fingerprint failure](https://github.com/RookieEnough/De-Vanced/issues/97), [Rushi Messenger catalog](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md).
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/links/` hook and settings control.
  Acceptance: HTTP and HTTPS links open through Android's browser chooser or default browser; internal Messenger routes still open inside Messenger.
  Complexity: M

- [ ] P2: Check update prompts, chat heads and media quality on the exact target.
  Why: Existing catalogs list these features, but the current target and Android 9 compatibility have not been established.
  Evidence: [Rushi Messenger catalog](https://github.com/rushiranpise/morphe-patches/blob/main/PATCHES.md), [chat-head request](https://github.com/RookieEnough/De-Vanced/issues/91), [native-bubble request](https://github.com/RookieEnough/De-Vanced/issues/47), `RESEARCH.md` version constraint.
  Touches: new `patches/src/main/kotlin/app/hushmessenger/patches/updates/`, `chatheads/`, `media/`.
  Acceptance: Separate opt-in patches pass fixture checks and user-visible tests; media comparison uses identical input files, while chat heads and native bubbles are tested as distinct modes and respect Android's permission flow.
  Complexity: L

- [ ] P2: Prepare repeatable local distribution and diagnostics.
  Why: A patch source needs clear build requirements, versioned metadata and a way to explain failed compatibility checks.
  Evidence: [Morphe template](https://github.com/MorpheApp/morphe-patches-template), [ReVanced Manager patching flow](https://github.com/ReVanced/revanced-manager/blob/main/docs/2_1_patching.md).
  Touches: `scripts/`, `README.md`, `CHANGELOG.md`, `patches/build.gradle.kts`, bundle metadata.
  Acceptance: A clean local build produces `patches-<version>.mpp`, a mismatch gives package/version/hash diagnostics, and release instructions cover signing, rollback and an English plus one non-English device check.
  Complexity: M
