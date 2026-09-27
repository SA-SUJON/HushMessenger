"""Confirm that a changed Messenger DEX fails before Morphe writes an APK."""

import argparse
import hashlib
import json
import subprocess
import tempfile
import zlib
from pathlib import Path
from zipfile import ZipFile

STOCK_SHA256 = "128ec75e836f24328d2b28777091c03b20abba0adc536e7ee911ee5fe52e70bc"
OLD_LITERAL = b"com.facebook.permission.prod.FB_APP_COMMUNICATION"
CHANGED_LITERAL = b"com.facebook.permission.proX.FB_APP_COMMUNICATION"


def altered_dex(data: bytes) -> bytes:
    changed = bytearray(data.replace(OLD_LITERAL, CHANGED_LITERAL))
    changed[12:32] = hashlib.sha1(changed[32:]).digest()
    changed[8:12] = (zlib.adler32(changed[12:]) & 0xFFFFFFFF).to_bytes(4, "little")
    return bytes(changed)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stock-apk", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--desktop-jar", type=Path, required=True)
    parser.add_argument("--java", type=Path, default=Path("java"))
    args = parser.parse_args()

    with args.stock_apk.open("rb") as source:
        actual_hash = hashlib.file_digest(source, "sha256").hexdigest()
    if actual_hash != STOCK_SHA256:
        parser.error(f"stock APK SHA-256 mismatch: {actual_hash}")

    with tempfile.TemporaryDirectory(prefix="hushmessenger-drift-") as scratch:
        root = Path(scratch)
        changed_apk = root / "changed.apk"
        output_apk = root / "unexpected-output.apk"
        report_path = root / "result.json"
        changed_sites = 0
        with ZipFile(args.stock_apk) as source, ZipFile(changed_apk, "w") as target:
            for entry in source.infolist():
                data = source.read(entry.filename)
                if entry.filename.endswith(".dex") and OLD_LITERAL in data:
                    changed_sites += data.count(OLD_LITERAL)
                    data = altered_dex(data)
                target.writestr(entry, data)
        if changed_sites != 2:
            raise RuntimeError(f"expected two stock DEX literals, found {changed_sites}")

        command = [
            str(args.java),
            "-jar",
            str(args.desktop_jar),
            "patch",
            f"-p={args.bundle}",
            "--unsigned",
            f"-r={report_path}",
            f"-o={output_apk}",
            str(changed_apk),
        ]
        run = subprocess.run(command, capture_output=True, text=True, timeout=300, check=False)
        report = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {}
        log = run.stdout + run.stderr
        failed = report.get("failedPatches", [])
        patch_failure = next(
            (item for item in failed if item.get("patch", {}).get("name") == "Install beside Meta apps"),
            None,
        )
        steps = report.get("patchingSteps", [])
        if output_apk.exists() or run.returncode == 0 or patch_failure is None or any(
            step.get("step") in ("REBUILDING", "SIGNING") for step in steps
        ):
            print(log[-4000:])
            print(json.dumps(report, sort_keys=True))
            raise RuntimeError("changed APK was not rejected by the permission patch")
        reason = patch_failure.get("reason", "").partition("\n")[0].rstrip("\r")
        if "expected 6 permission loads, found 4" not in reason or "version code 346013387" not in reason:
            print(reason)
            raise RuntimeError("failure did not identify the changed permission DEX sites")
        print(f"Changed APK rejected before output: {reason}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
