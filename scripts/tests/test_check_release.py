import copy
import hashlib
import importlib.util
import io
import json
import re
import shutil
import struct
import subprocess
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "check_release", Path(__file__).parents[1] / "check_release.py"
)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

SSH_KEYGEN = shutil.which("ssh-keygen")
REPO = Path(__file__).parents[2]


def new_key(path):
    subprocess.run(
        [SSH_KEYGEN, "-q", "-t", "ed25519", "-N", "", "-C", "test", "-f", str(path)],
        check=True,
        capture_output=True,
    )
    return path.with_name(path.name + ".pub").read_text(encoding="utf-8").split()[:2]


def sign(key, file, namespace=release.SIGNATURE_NAMESPACE):
    signature = file.with_name(file.name + ".sig")
    signature.unlink(missing_ok=True)
    subprocess.run(
        [SSH_KEYGEN, "-Y", "sign", "-f", str(key), "-n", namespace, str(file)],
        check=True,
        capture_output=True,
    )


class ReleaseChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "patches/build/libs/patches-1.2.3.mpp"
        self.bundle.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\r\nVersion: 1.2.3\r\nTimestamp: 1790552148000\r\n\r\n",
            )
            archive.writestr("classes.dex", b"fixture")
            archive.writestr("extensions/messenger.mpe", b"fixture")
        self.digest = hashlib.sha256(self.bundle.read_bytes()).hexdigest()
        self.catalog = {
            "version": "1.2.3",
            "patches": [
                {"name": f"Control {n}", "default": True, "dependencies": []}
                for n in range(32)
            ],
        }
        self.index = {
            "version": "1.2.3",
            "created_at": "2026-09-27T23:35:48",
            "download_url": "https://github.com/SysAdminDoc/HushMessenger/releases/download/v1.2.3/patches-1.2.3.mpp",
        }
        self.write(
            "gradle.properties", "version=1.2.3\nbundleTimestampMillis=1790552148000\n"
        )
        self.write("patches-bundle.json", json.dumps(self.index))
        self.write("patches-list.json", json.dumps(self.catalog))
        self.write(
            "patches/build/reports/catalog-evidence.json",
            json.dumps(
                {
                    "bundle": self.bundle.name,
                    "sha256": self.digest,
                    "catalog": self.catalog,
                }
            ),
        )
        self.write(
            "README.md",
            f"https://img.shields.io/badge/version-1.2.3-blue\n{self.digest}  patches-1.2.3.mpp\n",
        )
        self.write("CHANGELOG.md", "# Changelog\n\n## 1.2.3 (2026-09-27)\n")
        self.write(
            "extensions/messenger/build.gradle.kts",
            "versionName = project.version.toString()\n",
        )

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    def test_complete_release_passes_and_explicit_tag_or_checksum_mismatch_fails(self):
        self.assertIn(
            "Release metadata passed", release.verify(self.root, release_tag="v1.2.3")
        )
        with self.assertRaisesRegex(ValueError, "tag differs"):
            release.verify(self.root, release_tag="v1.2.4")
        self.write("SHA256SUMS.txt", f"{self.digest}  {self.bundle.name}\n")
        release.verify(self.root, checksums=self.root / "SHA256SUMS.txt")
        self.write("SHA256SUMS.txt", f"{'0' * 64}  {self.bundle.name}\n")
        with self.assertRaisesRegex(ValueError, "checksum file"):
            release.verify(self.root, checksums=self.root / "SHA256SUMS.txt")

    def test_development_freezes_current_catalog_and_preserves_held_feed(self):
        self.write("README.md", "https://img.shields.io/badge/development-1.2.3-blue\n")
        self.write("CHANGELOG.md", "## Unreleased\n")
        self.write(
            "patches-bundle.json",
            json.dumps(
                {
                    **self.index,
                    "version": "1.2.2",
                    "download_url": self.index["download_url"].replace(
                        "1.2.3", "1.2.2"
                    ),
                }
            ),
        )
        # Public releases used 32; this development fixture intentionally contains 33.
        self.catalog["patches"].append({"name": "New control"})
        self.write("patches-list.json", json.dumps(self.catalog))
        held = hashlib.sha256(
            (self.root / "patches-bundle.json").read_bytes()
        ).hexdigest()
        destination = self.root / "frozen"

        def validate(root, bundle, evidence):
            self.assertFalse(release.mutable_output(root, bundle))
            evidence.write_text(
                json.dumps(
                    {
                        "bundle": bundle.name,
                        "sha256": hashlib.sha256(bundle.read_bytes()).hexdigest(),
                        "dexValidated": True,
                        "catalog": self.catalog,
                    }
                )
            )

        with patch.object(release, "validate_catalog", side_effect=validate):
            output = io.StringIO()
            with redirect_stdout(output):
                self.assertEqual(
                    0,
                    release.main(
                        [
                            "--root",
                            str(self.root),
                            "--development",
                            "--held-index-sha256",
                            held,
                            "--freeze",
                            str(destination),
                        ]
                    ),
                )
            self.assertIn("33 patches, held public v1.2.2", output.getvalue())
            frozen = destination / self.bundle.name
            sums = destination / "SHA256SUMS.txt"
            cli = [
                "--root",
                str(self.root),
                "--development",
                "--held-index-sha256",
                held,
                "--bundle",
                str(frozen),
                "--bundle-sha256",
                self.digest,
                "--checksums",
                str(sums),
            ]
            # A later producer write doesn't touch the validated snapshot.
            self.bundle.write_bytes(b"later Java-only producer")
            with redirect_stdout(io.StringIO()):
                self.assertEqual(0, release.main(cli))
            self.assertEqual(
                self.digest, hashlib.sha256(frozen.read_bytes()).hexdigest()
            )
            frozen_data = frozen.read_bytes()

            def changed_during_signature(root, checksum):
                frozen.write_bytes(b"changed while verifying signature")
                return "Good signature"

            with (
                patch.object(
                    release, "verify_signature", side_effect=changed_during_signature
                ),
                redirect_stdout(io.StringIO()),
                redirect_stderr(io.StringIO()),
            ):
                self.assertEqual(1, release.main([*cli, "--verify-signature"]))
            frozen.write_bytes(frozen_data)
            for path, data in [
                (frozen, b"changed"),
                (self.root / "patches-bundle.json", b"changed held feed"),
            ]:
                original = path.read_bytes()
                path.write_bytes(data)
                error = io.StringIO()
                with redirect_stderr(error):
                    self.assertEqual(1, release.main(cli))
                self.assertIn("changed", error.getvalue().lower())
                path.write_bytes(original)

    def test_release_count_follows_current_exact_catalog_without_relaxing_metadata(
        self,
    ):
        self.catalog["patches"].append({"name": "New control"})
        self.write("patches-list.json", json.dumps(self.catalog))
        self.write(
            "patches/build/reports/catalog-evidence.json",
            json.dumps(
                {
                    "bundle": self.bundle.name,
                    "sha256": self.digest,
                    "catalog": self.catalog,
                }
            ),
        )
        self.assertIn("33 patches", release.verify(self.root))
        self.catalog["patches"][-1]["name"] = "Control 0"
        self.write("patches-list.json", json.dumps(self.catalog))
        self.write(
            "patches/build/reports/catalog-evidence.json",
            json.dumps(
                {
                    "bundle": self.bundle.name,
                    "sha256": self.digest,
                    "catalog": self.catalog,
                }
            ),
        )
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            release.verify(self.root)

    def test_freeze_rejects_mutable_destination_changed_producer_and_catalog_drift(
        self,
    ):
        self.write("README.md", "https://img.shields.io/badge/development-1.2.3-blue\n")
        self.write("CHANGELOG.md", "## Unreleased\n")
        held = hashlib.sha256(
            (self.root / "patches-bundle.json").read_bytes()
        ).hexdigest()
        with self.assertRaisesRegex(ValueError, "mutable Gradle"):
            release.freeze_development(
                self.root, self.bundle, self.bundle.parent / "frozen", held
            )
        destination = self.root / "frozen"
        original = self.bundle.read_bytes()
        for case in ["producer", "catalog", "version", "hash", "dex"]:

            def validate(root, bundle, evidence, case=case):
                catalog = copy.deepcopy(self.catalog)
                if case == "catalog":
                    catalog["patches"][0]["default"] = False
                if case == "version":
                    self.write(
                        "gradle.properties",
                        "version=1.2.4\nbundleTimestampMillis=1790552148000\n",
                    )
                if case == "producer":
                    self.bundle.write_bytes(b"producer changed during validation")
                evidence.write_text(
                    json.dumps(
                        {
                            "bundle": bundle.name,
                            "sha256": self.digest if case != "hash" else "0" * 64,
                            "dexValidated": case != "dex",
                            "catalog": catalog,
                        }
                    )
                )

            with (
                patch.object(release, "validate_catalog", side_effect=validate),
                self.assertRaises(ValueError),
            ):
                release.freeze_development(self.root, self.bundle, destination, held)
            self.assertFalse(destination.exists())
            self.bundle.write_bytes(original)
            self.write(
                "gradle.properties",
                "version=1.2.3\nbundleTimestampMillis=1790552148000\n",
            )

    def test_catalog_validation_failure_does_not_reuse_old_evidence(self):
        evidence = self.root / "evidence.json"
        evidence.write_text("old evidence")
        with (
            patch.object(
                release.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 1, "invalid DEX", ""),
            ),
            self.assertRaisesRegex(ValueError, "invalid DEX"),
        ):
            release.validate_catalog(self.root, self.bundle, evidence)
        self.assertFalse(evidence.exists())

    def test_saved_catalog_comparison_preserves_json_types_and_object_order(self):
        evidence = self.root / "patches/build/reports/catalog-evidence.json"
        held = hashlib.sha256(
            (self.root / "patches-bundle.json").read_bytes()
        ).hexdigest()
        for development in (False, True):
            with self.subTest(development=development):
                if development:
                    self.write(
                        "README.md",
                        "https://img.shields.io/badge/development-1.2.3-blue\n",
                    )
                    self.write("CHANGELOG.md", "## Unreleased\n")

                def check(development=development):
                    if development:
                        return release.verify_development(
                            self.root, self.bundle, evidence, held
                        )
                    return release.verify(self.root)

                # Object member order has no meaning, but booleans aren't numbers.
                reordered = {"patches": self.catalog["patches"], "version": "1.2.3"}
                payload = {
                    "bundle": self.bundle.name,
                    "sha256": self.digest,
                    "dexValidated": True,
                    "catalog": reordered,
                }
                evidence.write_text(json.dumps(payload))
                check()
                for number in (1, 1.0):
                    changed = copy.deepcopy(payload)
                    changed["catalog"]["patches"][0]["default"] = number
                    evidence.write_text(json.dumps(changed))
                    with (
                        self.subTest(number=number),
                        self.assertRaisesRegex(ValueError, "catalog|Catalog"),
                    ):
                        check()

    @unittest.skipIf(SSH_KEYGEN is None, "ssh-keygen isn't installed")
    def test_signed_checksums_pass_and_an_edit_another_key_or_namespace_fails(self):
        key, other = self.root / "release_key", self.root / "other_key"
        key_type, blob = new_key(key)
        new_key(other)
        self.write(
            "scripts/release_signers",
            f'# test\nSysAdminDoc namespaces="hushmessenger-release" {key_type} {blob}\n',
        )
        checksums = self.root / "SHA256SUMS.txt"
        self.write("SHA256SUMS.txt", f"{self.digest}  {self.bundle.name}\n")
        with self.assertRaisesRegex(ValueError, "Missing signature"):
            release.verify_signature(self.root, checksums)
        sign(key, checksums)
        self.assertRegex(
            release.verify_signature(self.root, checksums),
            r'Good "hushmessenger-release" signature for SysAdminDoc with ED25519 key '
            + re.escape(
                release.signer_fingerprints(self.root / "scripts/release_signers")[0]
            ),
        )
        output, error = io.StringIO(), io.StringIO()
        cli = [
            "--root",
            str(self.root),
            "--checksums",
            str(checksums),
            "--verify-signature",
        ]
        with redirect_stdout(output):
            self.assertEqual(0, release.main(cli))
        self.assertIn("Good", output.getvalue())
        self.write("SHA256SUMS.txt", f"{'0' * 64}  {self.bundle.name}\n")
        with self.assertRaisesRegex(ValueError, "isn't signed by the key"):
            release.verify_signature(self.root, checksums)
        self.write("SHA256SUMS.txt", f"{self.digest}  {self.bundle.name}\n")
        for signer, namespace in [(other, release.SIGNATURE_NAMESPACE), (key, "file")]:
            sign(signer, checksums, namespace)
            with (
                self.subTest(namespace=namespace),
                self.assertRaisesRegex(ValueError, "isn't signed by the key"),
            ):
                release.verify_signature(self.root, checksums)
            with redirect_stdout(io.StringIO()), redirect_stderr(error):
                self.assertEqual(1, release.main(cli))
        self.assertIn("CHECK FAILED:", error.getvalue())
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            release.main(["--root", str(self.root), "--verify-signature"])

    def test_committed_release_key_is_the_one_the_readme_names(self):
        signers = REPO / release.RELEASE_SIGNERS
        lines = [
            line.split()
            for line in signers.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")
        ]
        self.assertEqual(1, len(lines))
        self.assertEqual(
            [
                release.RELEASE_SIGNER,
                f'namespaces="{release.SIGNATURE_NAMESPACE}"',
                "ssh-ed25519",
            ],
            lines[0][:3],
        )
        [fingerprint] = release.signer_fingerprints(signers)
        self.assertIn(
            f"`{fingerprint}`", (REPO / "README.md").read_text(encoding="utf-8")
        )

    def test_changed_catalog_metadata_and_stale_artifact_evidence_fail(self):
        for key, value in [
            ("name", "Different"),
            ("default", False),
            ("category", "Changed"),
            ("dependencies", ["Different"]),
        ]:
            altered = copy.deepcopy(self.catalog)
            altered["patches"][0][key] = value
            self.write("patches-list.json", json.dumps(altered))
            with (
                self.subTest(key=key),
                self.assertRaisesRegex(ValueError, "catalog differs"),
            ):
                release.verify(self.root)
        self.write("patches-list.json", json.dumps(self.catalog))
        with self.bundle.open("ab") as handle:
            handle.write(b"changed")
        with self.assertRaisesRegex(ValueError, "evidence is stale"):
            release.verify(self.root)

    def test_version_url_timestamp_readme_and_extension_drift_fail(self):
        cases = [
            (
                "patches-bundle.json",
                json.dumps({**self.index, "version": "1.2.4"}),
                "index version",
            ),
            (
                "patches-bundle.json",
                json.dumps(
                    {
                        **self.index,
                        "download_url": self.index["download_url"].replace(
                            "v1.2.3", "v1.2.4"
                        ),
                    }
                ),
                "download URL",
            ),
            (
                "patches-bundle.json",
                json.dumps(
                    {**self.index, "created_at": self.index["created_at"] + "Z"}
                ),
                "local date-time",
            ),
            (
                "patches-bundle.json",
                json.dumps({**self.index, "created_at": "2026-09-27T23:35:49"}),
                "timestamps differ",
            ),
            (
                "README.md",
                f"https://img.shields.io/badge/version-1.2.4-blue\n{self.digest}  {self.bundle.name}\n",
                "badge",
            ),
            (
                "README.md",
                f"https://img.shields.io/badge/version-1.2.3-blue\n{'0' * 64}  {self.bundle.name}\n",
                "checksum",
            ),
            (
                "README.md",
                (
                    '<a href="https://github.com/SysAdminDoc/HushMessenger/releases/tag/v1.2.2">'
                    f'<img src="https://img.shields.io/badge/version-1.2.3-blue"></a>\n{self.digest}  {self.bundle.name}\n'
                ),
                "download link",
            ),
            (
                "README.md",
                f"https://img.shields.io/badge/version-1.2.3-blue\n[notes](../../releases/tag/v1.2.2)\n{self.digest}  {self.bundle.name}\n",
                "download link",
            ),
            (
                "README.md",
                f"https://img.shields.io/badge/version-1.2.3-blue\ngithub.com/sysadmindoc/hushmessenger/releases/download/v1.2.2/x\n{self.digest}  {self.bundle.name}\n",
                "download link",
            ),
            ("CHANGELOG.md", "## Unreleased\n\n## 1.2.3 (2026-09-27)\n", "changelog"),
            (
                "extensions/messenger/build.gradle.kts",
                'versionName = "1.2.2"\n',
                "Extension version",
            ),
        ]
        for path, changed, message in cases:
            original = (self.root / path).read_text(encoding="utf-8")
            self.write(path, changed)
            with (
                self.subTest(path=path, message=message),
                self.assertRaisesRegex(ValueError, message),
            ):
                release.verify(self.root)
            self.write(path, original)

    def test_readme_may_link_another_projects_release(self):
        self.write(
            "README.md",
            "https://img.shields.io/badge/version-1.2.3-blue\n"
            "[Desktop](https://github.com/MorpheApp/morphe-desktop/releases/tag/v1.17.0)\n"
            "Pair it with hushfacebook-patches-0.1.7.mpp or morphe-patches-1.2.0.\n"
            '<a href="https://github.com/SysAdminDoc/HushMessenger/releases/tag/v1.2.3">ours</a>\n'
            f"{self.digest}  {self.bundle.name}\n",
        )
        self.assertIn("Release metadata passed", release.verify(self.root))

    def test_cli_rejects_malformed_or_missing_evidence_without_traceback(self):
        for invalid in ['{"version": "1", "version": "2"}', "[]", "{"]:
            self.write("patches/build/reports/catalog-evidence.json", invalid)
            error = io.StringIO()
            with redirect_stderr(error):
                self.assertEqual(1, release.main(["--root", str(self.root)]))
            self.assertIn("CHECK FAILED:", error.getvalue())
            self.assertNotIn("Traceback", error.getvalue())
        self.bundle.unlink()
        error = io.StringIO()
        with redirect_stderr(error):
            self.assertEqual(1, release.main(["--root", str(self.root)]))
        self.assertIn("CHECK FAILED:", error.getvalue())

    def test_corrupt_deflate_manifest_has_a_controlled_cli_error(self):
        with zipfile.ZipFile(self.bundle) as archive:
            entries = [(name, archive.read(name)) for name in archive.namelist()]
        with zipfile.ZipFile(
            self.bundle, "w", compression=zipfile.ZIP_DEFLATED
        ) as archive:
            for name, data in entries:
                archive.writestr(name, data)
        with zipfile.ZipFile(self.bundle) as archive:
            offset = archive.getinfo("META-INF/MANIFEST.MF").header_offset
        raw = bytearray(self.bundle.read_bytes())
        name_length, extra_length = struct.unpack_from("<HH", raw, offset + 26)
        raw[offset + 30 + name_length + extra_length] = 0x07
        self.bundle.write_bytes(raw)
        error = io.StringIO()
        with redirect_stderr(error):
            self.assertEqual(1, release.main(["--root", str(self.root)]))
        self.assertIn("CHECK FAILED:", error.getvalue())
        self.assertNotIn("Traceback", error.getvalue())


if __name__ == "__main__":
    unittest.main()
