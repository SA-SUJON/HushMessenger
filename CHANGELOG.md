# Changelog

## Unreleased

- Reject Messenger builds whose shared-permission manifest counts, component roles or active DEX loads differ from the checked 580 APK.
- Add manifest regression tests for missing guards, reassigned guards and duplicate declarations.
- Use the smali revision requested by Morphe Patcher 1.14.1.

## 0.0.2 (2026-09-27)

- Published the Messenger patch source with a README hero, icon and social preview in the Hush project style.
- Added a Morphe source index and a preview bundle for the exact Messenger 580 arm64 target.
- Rebuilt the bundle locally, applied it to a stock APK copy and verified the signed output off-device.
- Documented the signing-key requirement and the remaining phone checks.

## 0.0.1 (2026-09-27)

- Started a separate local Morphe patch source for Messenger `580.0.0.49.91` (arm64, version code `346013387`).
- Added a shared-permission rename patch for installation beside Meta apps.
- Added the project research, roadmap, build notes and GPL attribution.
