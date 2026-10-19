import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import zipfile

Root = pathlib.Path(__file__).resolve().parents[1]
Arguments = argparse.ArgumentParser()
Arguments.add_argument("--strip-tool", required=True)
Options = Arguments.parse_args()
Output = Root / "dist"
Output.mkdir(exist_ok=True)
Apk = Root / "manager/build/outputs/apk/debug/manager-debug.apk"
Library = Root / "native/build/libdeviceidchanger.so"
Dex = Root / "hook/build/hook.dex"
for Artifact in (Apk, Library, Dex):
    if not Artifact.is_file() or not Artifact.stat().st_size:
        raise RuntimeError(f"Build the missing artifact first: {Artifact}")

ReleaseDirectory = Root / "build/release"
ReleaseDirectory.mkdir(parents=True, exist_ok=True)
StrippedLibrary = ReleaseDirectory / "libdeviceidchanger.so"
subprocess.run([Options.strip_tool, "--strip-unneeded", "-o", str(StrippedLibrary), str(Library)], check=True)

Files = {}
for Source in (Root / "module-native").iterdir():
    if Source.is_file() and Source.suffix in (".sh", ".prop"):
        Files[Source.name] = Source
Files.update({"manager.apk": Apk, "hook.dex": Dex, "zygisk/arm64-v8a.so": StrippedLibrary, "LICENSE": Root / "LICENSE", "THIRD_PARTY_NOTICES.txt": Root / "THIRD_PARTY_NOTICES.txt"})
IndexPath = Root / "licenses/index.json"
Index = json.loads(IndexPath.read_text(encoding="utf-8"))
if Index.get("schema_version") != 1 or not Index.get("files"):
    raise RuntimeError("Third-party license index is invalid")
Files["licenses/index.json"] = IndexPath
for Notice in Index["files"]:
    Name = Notice["file"]
    if pathlib.PurePath(Name).name != Name:
        raise ValueError("Invalid third-party license filename")
    Source = Root / "licenses" / Name
    if hashlib.sha256(Source.read_bytes()).hexdigest() != Notice["sha256"]:
        raise ValueError(f"License checksum mismatch: {Name}")
    Files[f"licenses/{Name}"] = Source

Archive = Output / "deviceidchanger-0.1.0-prototype-debug.zip"
with zipfile.ZipFile(Archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as Package:
    for Name, Source in sorted(Files.items()):
        Contents = Source.read_bytes()
        if Name.endswith(".sh") and b"\r" in Contents:
            raise ValueError(f"Android script must have LF line endings: {Name}")
        Entry = zipfile.ZipInfo(Name, (2026, 10, 18, 0, 0, 0))
        Entry.create_system = 3
        Entry.external_attr = (0o100755 if Name.endswith(".sh") else 0o100644) << 16
        Entry.compress_type = zipfile.ZIP_DEFLATED
        Package.writestr(Entry, Contents)

Manager = Output / "deviceidchanger-0.1.0-prototype-debug.apk"
shutil.copyfile(Apk, Manager)
Checksums = []
for Artifact in (Archive, Manager):
    with Artifact.open("rb") as Stream:
        Digest = hashlib.file_digest(Stream, "sha256").hexdigest()
    Checksums.append(f"{Digest}  {Artifact.name}")
(Output / "SHA256SUMS").write_text("\n".join(Checksums) + "\n", encoding="utf-8", newline="\n")
print(f"Created {Archive}")
print(f"Created {Manager}")
