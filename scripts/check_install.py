#!/usr/bin/env python3
"""Check Messenger APK certificates against a phone without changing the phone."""

import argparse
import hashlib
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Apk:
    package: str
    version_code: int
    version_name: str
    permissions: frozenset[str]
    signers: frozenset[str]


def run(command: list[str]) -> str:
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=120,
        check=False,
    )
    if result.returncode:
        detail = (result.stderr or result.stdout).strip()[-2000:]
        raise ValueError(
            f"{Path(command[0]).name} failed ({result.returncode}): {detail}"
        )
    return result.stdout


def active_signers(output: str, sdk: int) -> frozenset[str]:
    """Select current APK signers for this API level, excluding the source stamp."""
    count = re.search(r"^Number of signers: (\d+)$", output, re.MULTILINE)
    records = re.findall(
        r"^Signer .*certificate SHA-256 digest:.*$", output, re.MULTILINE
    )
    selected = []
    for record in records:
        match = re.fullmatch(
            r"Signer (?:#\d+|\(minSdkVersion=(\d+), maxSdkVersion=(\d+)\)) "
            r"certificate SHA-256 digest: ([0-9a-fA-F]{64})",
            record,
        )
        if not match:
            raise ValueError("Unrecognized apksigner certificate output")
        lower, upper, digest = match.groups()
        if lower is None or int(lower) <= sdk <= int(upper):
            selected.append(digest.lower())
    if (
        not count
        or not selected
        or len(selected) != int(count[1])
        or len(set(selected)) != len(selected)
    ):
        raise ValueError(
            "Cannot establish the complete current signer set for this Android version"
        )
    return frozenset(selected)


def permission_owners(output: str) -> dict[str, str]:
    owners = {}
    permission = None
    for line in output.splitlines():
        match = re.fullmatch(r"\s*\+ permission:([\w.]+)\s*", line)
        if match:
            if permission is not None:
                raise ValueError(f"Missing permission owner: {permission}")
            permission = match[1]
        elif permission is not None:
            match = re.fullmatch(r"\s*package:([A-Za-z0-9_.]+)\s*", line)
            if match:
                if permission in owners:
                    raise ValueError(f"Duplicate permission owner record: {permission}")
                owners[permission] = match[1]
                permission = None
    if permission is not None or "android.permission.INTERNET" not in owners:
        raise ValueError("Incomplete device permission inventory")
    return owners


def read_apk(path: Path, args: argparse.Namespace, sdk: int) -> Apk:
    aapt = str(args.build_tools / ("aapt2.exe" if os.name == "nt" else "aapt2"))
    badging = run([aapt, "dump", "badging", str(path)])
    match = re.search(
        r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",
        badging,
        re.MULTILINE,
    )
    if not match or not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", match[1]):
        raise ValueError("Cannot read APK package and version")
    permissions = frozenset(
        re.findall(
            r"^permission: ([A-Za-z0-9_.]+)\s*$",
            run([aapt, "dump", "permissions", str(path)]),
            re.MULTILINE,
        )
    )
    verified = run(
        [
            str(args.java),
            "-jar",
            str(args.build_tools / "lib" / "apksigner.jar"),
            "verify",
            "--verbose",
            "--print-certs",
            "--min-sdk-version",
            str(sdk),
            "--max-sdk-version",
            str(sdk),
            str(path),
        ]
    )
    return Apk(
        match[1], int(match[2]), match[3], permissions, active_signers(verified, sdk)
    )


def conflicts(
    candidate: Apk, installed: dict[str, Apk], owners: dict[str, str]
) -> list[str]:
    problems = []
    previous = installed.get(candidate.package)
    if previous:
        if previous.signers != candidate.signers:
            problems.append(
                f"{candidate.package}: current signing certificates differ from the installed app. "
                "Keep its data intact. This check does not approve certificate rotation or replacement."
            )
        if previous.version_code > candidate.version_code:
            problems.append(
                f"Version downgrade: installed {previous.version_code}, candidate {candidate.version_code}."
            )
    for permission in sorted(candidate.permissions):
        owner = owners.get(permission)
        if owner and owner != candidate.package:
            if owner not in installed:
                raise ValueError(f"Cannot verify permission owner {owner}")
            if installed[owner].signers != candidate.signers:
                problems.append(
                    f"{permission}: owned by {owner} with different current signing certificates. "
                    "Use the same signing key for apps sharing this permission."
                )
    return problems


def check(args: argparse.Namespace) -> int:
    adb = [str(args.adb), "-s", args.serial]
    if run(adb + ["get-state"]).strip() != "device":
        raise ValueError("The selected phone is not connected and authorized")
    sdk = int(run(adb + ["shell", "getprop", "ro.build.version.sdk"]).strip())
    if sdk < 28:
        raise ValueError("This preview requires Android 9 (API 28) or newer")
    candidate = read_apk(args.apk, args, sdk)
    if (
        candidate.package != "com.facebook.orca"
        or candidate.version_name != "580.0.0.49.91"
        or candidate.version_code not in {346013387, 346013440}
    ):
        raise ValueError(
            "Use Messenger 580.0.0.49.91, version code 346013387 or 346013440"
        )
    if not candidate.permissions:
        raise ValueError("Messenger permission declarations are missing")
    with args.apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    print(
        f"Candidate: {candidate.package} {candidate.version_name} ({candidate.version_code})"
    )
    print(f"APK SHA-256: {digest}")
    print(f"Device API: {sdk}; installed package lookup: user 0")
    print(f"Candidate certificate SHA-256: {', '.join(sorted(candidate.signers))}")

    owners = permission_owners(run(adb + ["shell", "pm", "list", "permissions", "-f"]))
    package_output = run(adb + ["shell", "pm", "list", "packages", "--user", "0"])
    packages = set(
        re.findall(r"^package:([A-Za-z0-9_.]+)\s*$", package_output, re.MULTILINE)
    )
    if "android" not in packages:
        raise ValueError("Incomplete installed package inventory")
    required = {owners[name] for name in candidate.permissions if name in owners}
    required.update(packages.intersection({candidate.package, "com.facebook.katana"}))
    installed = {}
    with tempfile.TemporaryDirectory(prefix="hushmessenger-certificates-") as scratch:
        for index, package in enumerate(sorted(required)):
            paths = run(
                adb + ["shell", "pm", "path", "--user", "0", package]
            ).splitlines()
            paths = [
                line.removeprefix("package:")
                for line in paths
                if line.startswith("package:")
            ]
            base = [path for path in paths if path.endswith("/base.apk")]
            if not base and len(paths) == 1:
                base = paths
            if len(base) != 1:
                raise ValueError(f"Cannot locate one readable base APK for {package}")
            local = Path(scratch) / f"{index}.apk"
            run(adb + ["pull", base[0], str(local)])
            info = read_apk(local, args, sdk)
            if info.package != package:
                raise ValueError(
                    f"Installed APK identity changed while checking {package}"
                )
            installed[package] = info
            print(
                f"Installed {package} certificate SHA-256: {', '.join(sorted(info.signers))}"
            )
    problems = conflicts(candidate, installed, owners)
    if problems:
        for problem in problems:
            print(f"CONFLICT: {problem}")
        return 1
    print(
        "Certificate check passed. Installation, cross-app trust and Messenger runtime behavior remain unverified."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--serial", required=True, help="Exact adb device serial")
    parser.add_argument(
        "--build-tools",
        type=Path,
        required=True,
        help="Android SDK Build Tools directory (tested with 36.1.0)",
    )
    parser.add_argument("--java", type=Path, default=Path("java"))
    parser.add_argument("--adb", type=Path, default=Path("adb"))
    args = parser.parse_args()
    try:
        return check(args)
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(f"CHECK FAILED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
