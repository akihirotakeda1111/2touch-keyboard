"""Identify, package and verify the exact Mozc dependency (standard library only)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def identity(root=ROOT):
    lock = json.loads((root / "mozc.lock.json").read_text(encoding="utf-8"))
    if lock["schema_version"] != 1 or not re.fullmatch(r"[0-9a-f]{40}", lock["commit"]):
        raise ValueError("Mozc must be pinned to a full commit SHA")
    if lock["repository"] != "google/mozc" or lock["abis"] != ["arm64-v8a"]:
        raise ValueError("Unsupported Mozc repository or ABI configuration")
    # Include all build inputs under our control, including the application JNI/proto contract.
    paths = [root / "mozc.lock.json", root / "scripts/build-mozc-android.sh",
             root / "scripts/mozc_artifact.py", root / ".github/workflows/build-mozc.yml"]
    paths += sorted((root / "mozc-engine/src/main/proto").rglob("*.proto"))
    java_paths = sorted((root / "mozc-engine/src/main/java/com/google").rglob("*.java"))
    paths += java_paths
    java_symbols = set()
    for path in java_paths:
        source = path.read_text(encoding="utf-8")
        package_name = re.search(r"package\s+([\w.]+)\s*;", source)
        class_name = re.search(r"public\s+final\s+class\s+(\w+)", source)
        if package_name and class_name and re.search(r"native\s+boolean\s+initialize\s*\(", source):
            java_symbols.add("Java_" + package_name[1].replace(".", "_") + "_" + class_name[1] + "_initialize")
    if lock["jni_symbol"] not in java_symbols:
        raise ValueError("Pinned JNI entry point does not match the application's Java wrapper")
    inputs = {p.relative_to(root).as_posix(): sha256(p.read_bytes().replace(b"\r\n", b"\n"))
              for p in paths}
    recipe = sha256(json.dumps(inputs, sort_keys=True).encode())
    return {"lock": lock, "recipe": recipe,
            "tag": f"mozc-{lock['commit']}-{recipe}"}


def payload_names(info):
    return [f"jniLibs/{abi}/libmozc.so" for abi in info["lock"]["abis"]] + ["assets/mozc.data"]


def verify_library(path, symbol):
    data = path.read_bytes()
    if data[:4] != b"\x7fELF" or data[4:6] != b"\x02\x01" or data[18:20] != b"\xb7\x00":
        raise ValueError(f"Not an arm64 ELF library: {path}")
    result = subprocess.run(["nm", "-D", "--defined-only", str(path)],
                            check=True, capture_output=True, text=True)
    symbols = {line.split()[-1] for line in result.stdout.splitlines() if line.split()}
    if symbol not in symbols:
        raise ValueError(f"Missing JNI entry point: {symbol}")


def package(source, output, info):
    payload = {name: (source / name).read_bytes() for name in payload_names(info)}
    if not all(payload.values()):
        raise ValueError("Mozc artifact contains an empty file")
    for abi in info["lock"]["abis"]:
        verify_library(source / f"jniLibs/{abi}/libmozc.so", info["lock"]["jni_symbol"])
    manifest = {**info, "files": {name: sha256(data) for name, data in payload.items()}}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in payload.items():
            archive.writestr(name, data)
        archive.writestr("manifest.json", json.dumps(manifest, indent=2) + "\n")
    output.with_suffix(".zip.sha256").write_text(sha256(output.read_bytes()) + "\n", encoding="ascii")


def verify(archive_path, destination, info):
    expected = archive_path.with_suffix(".zip.sha256").read_text(encoding="ascii").strip()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or sha256(archive_path.read_bytes()) != expected:
        raise ValueError("Mozc ZIP checksum mismatch")
    with zipfile.ZipFile(archive_path) as archive:
        names = payload_names(info)
        if sorted(archive.namelist()) != sorted(names + ["manifest.json"]):
            raise ValueError("Unexpected or duplicate files in Mozc artifact")
        manifest = json.loads(archive.read("manifest.json"))
        if any(manifest.get(key) != value for key, value in info.items()):
            raise ValueError("Mozc artifact does not match the pinned commit/build recipe")
        if set(manifest.get("files", {})) != set(names):
            raise ValueError("Invalid Mozc manifest")
        payload = {name: archive.read(name) for name in names}
        for name, data in payload.items():
            if not data or sha256(data) != manifest["files"][name]:
                raise ValueError(f"Mozc file checksum mismatch: {name}")
    # All paths come from the fixed allowlist, never from archive extraction.
    for name, data in payload.items():
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    for abi in info["lock"]["abis"]:
        verify_library(destination / f"jniLibs/{abi}/libmozc.so", info["lock"]["jni_symbol"])


def verify_apk(apk_path, source, info):
    with zipfile.ZipFile(apk_path) as apk:
        for name in payload_names(info):
            apk_name = name.replace("jniLibs/", "lib/", 1)
            if apk.namelist().count(apk_name) != 1:
                raise ValueError(f"APK is missing Mozc payload or contains duplicates: {apk_name}")
            if sha256(apk.read(apk_name)) != sha256((source / name).read_bytes()):
                raise ValueError(f"APK Mozc payload differs from verified dependency: {apk_name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["identity", "package", "verify", "verify-apk", "check-source"])
    parser.add_argument("--source", type=Path, default=ROOT / "mozc-engine/src/main")
    parser.add_argument("--archive", type=Path, default=ROOT / "build/mozc/mozc-android.zip")
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    info = identity()
    if args.command == "identity":
        outputs = {"tag": info["tag"], "commit": info["lock"]["commit"],
                   "bazel": info["lock"]["bazel_version"]}
        print(json.dumps(info, indent=2))
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
                stream.writelines(f"{key}={value}\n" for key, value in outputs.items())
    elif args.command == "check-source":
        actual = subprocess.check_output(["git", "-C", str(args.source), "rev-parse", "HEAD"], text=True).strip()
        if actual != info["lock"]["commit"]:
            raise ValueError(f"Wrong Mozc checkout: expected {info['lock']['commit']}, got {actual}")
        if subprocess.check_output(["git", "-C", str(args.source), "status", "--porcelain", "--untracked-files=no"], text=True).strip():
            raise ValueError("Mozc checkout has tracked modifications")
        if info["lock"]["jni_symbol"] not in (args.source / "android/jni/mozcjni.cc").read_text(encoding="utf-8"):
            raise ValueError("Mozc source JNI entry point does not match the application")
    elif args.command == "package":
        package(args.source, args.archive, info)
    elif args.command == "verify-apk":
        if args.apk is None:
            parser.error("verify-apk requires --apk")
        verify_apk(args.apk, args.source, info)
    else:
        verify(args.archive, args.source, info)


if __name__ == "__main__":
    main()
