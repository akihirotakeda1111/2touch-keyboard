import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import mozc_artifact as artifact


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.archive = self.root / "mozc-android.zip"
        self.destination = self.root / "installed"
        self.info = artifact.identity()
        for name in artifact.payload_names(self.info):
            path = self.source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"test data")
        self.native_check = patch.object(artifact, "verify_library")
        self.native_check.start()
        self.addCleanup(self.native_check.stop)
        artifact.package(self.source, self.archive, self.info)

    def rewrite(self, change):
        with zipfile.ZipFile(self.archive) as archive:
            contents = {name: archive.read(name) for name in archive.namelist()}
        change(contents)
        with zipfile.ZipFile(self.archive, "w") as archive:
            for name, data in contents.items():
                archive.writestr(name, data)
        self.archive.with_suffix(".zip.sha256").write_text(artifact.sha256(self.archive.read_bytes()))

    def test_round_trip(self):
        artifact.verify(self.archive, self.destination, self.info)
        for name in artifact.payload_names(self.info):
            self.assertEqual((self.destination / name).read_bytes(), b"test data")

    def test_rejects_wrong_commit_and_recipe(self):
        for field in ("commit", "recipe"):
            wrong = copy.deepcopy(self.info)
            if field == "commit":
                wrong["lock"][field] = "0" * 40
            else:
                wrong[field] = "0" * 64
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "pinned commit"):
                artifact.verify(self.archive, self.destination, wrong)
        self.assertFalse(self.destination.exists())

    def test_rejects_corrupt_archive(self):
        with self.archive.open("ab") as stream:
            stream.write(b"corrupt")
        with self.assertRaisesRegex(ValueError, "ZIP checksum"):
            artifact.verify(self.archive, self.destination, self.info)

    def test_rejects_corrupt_payload_even_with_updated_zip_checksum(self):
        self.rewrite(lambda files: files.update({"assets/mozc.data": b"changed"}))
        with self.assertRaisesRegex(ValueError, "file checksum"):
            artifact.verify(self.archive, self.destination, self.info)
        self.assertFalse(self.destination.exists())

    def test_rejects_unexpected_paths(self):
        self.rewrite(lambda files: files.update({"../outside": b"unexpected"}))
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            artifact.verify(self.archive, self.destination, self.info)
        self.assertFalse((self.root / "outside").exists())

    def test_rejects_empty_dictionary(self):
        (self.source / "assets/mozc.data").write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "empty"):
            artifact.package(self.source, self.archive, self.info)

    def test_apk_contains_exact_dependency(self):
        apk = self.root / "app.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            for name in artifact.payload_names(self.info):
                archive.writestr(name.replace("jniLibs/", "lib/", 1), b"test data")
        artifact.verify_apk(apk, self.source, self.info)
        (self.source / "assets/mozc.data").write_bytes(b"other dictionary")
        with self.assertRaisesRegex(ValueError, "differs"):
            artifact.verify_apk(apk, self.source, self.info)

    def test_apk_without_native_library_is_rejected(self):
        apk = self.root / "app.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("assets/mozc.data", b"test data")
        with self.assertRaisesRegex(ValueError, "missing"):
            artifact.verify_apk(apk, self.source, self.info)


class NativeContractTests(unittest.TestCase):
    def test_wrong_architecture_and_jni_name(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "libmozc.so"
            path.write_bytes(b"not ELF")
            with self.assertRaisesRegex(ValueError, "arm64 ELF"):
                artifact.verify_library(path, "MozcJNI_initialize")
            path.write_bytes(b"\x7fELF\x02\x01" + bytes(12) + b"\xb7\x00")
            with patch.object(artifact.subprocess, "run") as run:
                run.return_value.stdout = "00000000 T MozcJni_initialize\n"
                with self.assertRaisesRegex(ValueError, "Missing JNI"):
                    artifact.verify_library(path, "MozcJNI_initialize")
                run.return_value.stdout = "00000000 T MozcJNI_initialize\n"
                artifact.verify_library(path, "MozcJNI_initialize")


if __name__ == "__main__":
    unittest.main()
