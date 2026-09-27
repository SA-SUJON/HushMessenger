#!/usr/bin/env python3
"""Regressions for pre-install certificate and permission checks."""

import argparse
import io
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from scripts import check_install as checker

A = "a" * 64
B = "b" * 64
STAMP = "c" * 64
PERMISSION = "app.hushfacebook.receiver.permission.ACCESS"
OWNERS = f"+ permission:android.permission.INTERNET\n  package:android\n+ permission:{PERMISSION}\n  package:com.facebook.katana\n"


def apk(package="com.facebook.orca", signers=(A,), code=346013440):
    return checker.Apk(
        package, code, "580.0.0.49.91", frozenset({PERMISSION}), frozenset(signers)
    )


class CertificateChecks(unittest.TestCase):
    def test_rotated_signer_is_selected_for_device_api_and_stamp_is_excluded(self):
        output = (
            "Number of signers: 1\n"
            f"Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {B}\n"
            f"Signer (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: {A}\n"
            f"Source Stamp Signer certificate SHA-256 digest: {STAMP}\n"
        )
        self.assertEqual(checker.active_signers(output, 36), {B})
        self.assertEqual(checker.active_signers(output, 28), {A})

    def test_multiple_signers_require_full_set(self):
        output = f"Number of signers: 2\nSigner #1 certificate SHA-256 digest: {A}\nSigner #2 certificate SHA-256 digest: {B}\n"
        self.assertEqual(checker.active_signers(output, 28), {A, B})
        self.assertTrue(
            checker.conflicts(apk(signers=(A, B)), {"com.facebook.orca": apk()}, {})
        )

    def test_missing_unknown_and_duplicate_signer_records_fail_closed(self):
        for output in (
            "",
            f"Number of signers: 1\nSource Stamp Signer certificate SHA-256 digest: {A}\n",
            f"Number of signers: 2\nSigner #1 certificate SHA-256 digest: {A}\n",
            f"Number of signers: 1\nSigner unknown certificate SHA-256 digest: {A}\n",
            f"Number of signers: 2\nSigner #1 certificate SHA-256 digest: {A}\nSigner #2 certificate SHA-256 digest: {A}\n",
        ):
            with self.subTest(output=output), self.assertRaises(ValueError):
                checker.active_signers(output, 36)

    def test_changed_key_is_not_approved_as_rotation(self):
        problems = checker.conflicts(
            apk(signers=(B,)), {"com.facebook.orca": apk()}, {}
        )
        self.assertIn("does not approve certificate rotation", problems[0])

    def test_same_key_update_and_permission_owner_pass(self):
        installed = {
            "com.facebook.orca": apk(),
            "com.facebook.katana": apk("com.facebook.katana"),
        }
        self.assertEqual(
            checker.conflicts(apk(), installed, {PERMISSION: "com.facebook.katana"}), []
        )

    def test_different_key_owner_is_named(self):
        installed = {"com.facebook.katana": apk("com.facebook.katana", (B,))}
        problems = checker.conflicts(
            apk(), installed, {PERMISSION: "com.facebook.katana"}
        )
        self.assertIn(PERMISSION, problems[0])
        self.assertIn("com.facebook.katana", problems[0])

    def test_renamed_permissions_do_not_require_matching_stock_facebook_key(self):
        installed = {"com.facebook.katana": apk("com.facebook.katana", (B,))}
        self.assertEqual(
            checker.conflicts(
                apk(),
                installed,
                {"com.facebook.receiver.permission.ACCESS": "com.facebook.katana"},
            ),
            [],
        )

    def test_downgrade_and_unreadable_owner_fail(self):
        self.assertIn(
            "downgrade",
            checker.conflicts(apk(), {"com.facebook.orca": apk(code=346013441)}, {})[0],
        )
        with self.assertRaises(ValueError):
            checker.conflicts(apk(), {}, {PERMISSION: "com.facebook.katana"})

    def test_permission_inventory_must_be_complete_and_unambiguous(self):
        self.assertEqual(
            checker.permission_owners(OWNERS)[PERMISSION], "com.facebook.katana"
        )
        for output in (
            "",
            "Error: access denied",
            OWNERS + f"+ permission:{PERMISSION}\n",
            OWNERS + OWNERS,
        ):
            with self.subTest(output=output), self.assertRaises(ValueError):
                checker.permission_owners(output)

    def test_failed_signature_verification_cannot_supply_certificates(self):
        failure = subprocess.CompletedProcess(
            ["java"], 1, f"Signer #1 certificate SHA-256 digest: {A}", "DOES NOT VERIFY"
        )
        with (
            patch.object(checker.subprocess, "run", return_value=failure),
            self.assertRaisesRegex(ValueError, "DOES NOT VERIFY"),
        ):
            checker.run(["java", "-jar", "apksigner.jar", "verify"])

    def test_live_check_uses_only_read_only_adb_commands_and_removes_pulled_apks(self):
        calls = []
        pulled = []
        with tempfile.TemporaryDirectory() as root:
            candidate = Path(root) / "candidate.apk"
            candidate.write_bytes(b"fixture")
            args = argparse.Namespace(
                apk=candidate, serial="selected-phone", adb=Path("adb")
            )

            def device(command):
                calls.append(command)
                self.assertEqual(command[:3], ["adb", "-s", "selected-phone"])
                operation = command[3:]
                if operation == ["get-state"]:
                    return "device\n"
                if operation == ["shell", "getprop", "ro.build.version.sdk"]:
                    return "36\n"
                if operation == ["shell", "pm", "list", "permissions", "-f"]:
                    return OWNERS
                if operation == ["shell", "pm", "list", "packages", "--user", "0"]:
                    return "package:android\npackage:com.facebook.orca\npackage:com.facebook.katana\n"
                if operation[:5] == ["shell", "pm", "path", "--user", "0"]:
                    return f"package:/data/app/{operation[5]}/base.apk\npackage:/data/app/{operation[5]}/split.apk\n"
                if operation[0] == "pull":
                    path = Path(operation[2])
                    path.write_bytes(b"pulled APK")
                    pulled.append(path)
                    return ""
                self.fail(f"Unexpected device operation: {operation}")

            with (
                patch.object(checker, "run", side_effect=device),
                patch.object(
                    checker,
                    "read_apk",
                    side_effect=[apk(), apk("com.facebook.katana"), apk()],
                ),
                redirect_stdout(io.StringIO()),
            ):
                self.assertEqual(checker.check(args), 0)
            self.assertEqual(len(pulled), 2)
            self.assertTrue(all(not path.exists() for path in pulled))
            self.assertTrue(calls)


if __name__ == "__main__":
    unittest.main()
