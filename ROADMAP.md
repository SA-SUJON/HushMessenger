# HushMessenger roadmap

No active implementation items remain. The settings redesign shipped in v0.3.0. Phone source refreshes are queued with their availability requirements in [Roadmap_Blocked.md](Roadmap_Blocked.md).

Stock S22 and S25 now have separate signed-in test accounts. Two-way encrypted messages and the S22's HTTP/HTTPS browser preference have been exercised. `scripts/check_install.py` checks signing conflicts without changing either phone.

The remaining acceptance checks are in [Roadmap_Blocked.md](Roadmap_Blocked.md). Account access is available; the unresolved re-signed startup failure, required fixtures and isolated system-setting checks are listed separately there. A working stock chat isn't evidence that a re-signed APK works.

[Issue 1](https://github.com/SysAdminDoc/HushMessenger/issues/1) reports unvalidated version code 346013370. Its log explains the rejection; the exact APK is required before support can be assessed.
