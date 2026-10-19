import argparse
import datetime
import hashlib
import io
import json
import pathlib
import stat
import subprocess
import tarfile
import zipfile


Root = pathlib.Path(__file__).resolve().parents[1]
Dependencies = (
    ("lsplant", "lsplant", "lsplant", "6ac2280d8d88e14f0864c26ac97c1dc96713bde1", "https://github.com/LSPosed/LSPlant"),
    ("dex_builder", "lsplant/lsplant/src/main/jni/external/dex_builder", "lsplant/lsplant/src/main/jni/external/dex_builder", "ac7fb2230954ee311808bad469b0db501f31bfb8", "https://github.com/LSPosed/DexBuilder"),
    ("parallel_hashmap", "lsplant/lsplant/src/main/jni/external/dex_builder/external/parallel_hashmap", "lsplant/lsplant/src/main/jni/external/dex_builder/external/parallel_hashmap", "0cd57d29a959256ed66b2afdd1009928fc625d09", "https://github.com/greg7mdp/parallel-hashmap"),
    ("dobby", "dobby", "dobby", "0c29f641c7e932af9ff99f4abf9ef98040a5bbba", "https://github.com/jmpews/Dobby"),
    ("xdl", "xdl", "xdl", "1e0b6254165a2ddcbd32f77a371700c69155acf8", "https://github.com/hexhacking/xDL"),
    ("zygisk", "zygisk", "zygisk", "7bb941ac8edfcffd1d23761e401c45ca95409dc1", "https://github.com/topjohnwu/zygisk-module-sample"),
)


def Git(Directory, *Arguments):
    Result = subprocess.run(("git", "-C", str(Directory), *Arguments), check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if Result.returncode:
        raise RuntimeError(Result.stderr.decode("utf-8", errors="replace").strip())
    return Result.stdout


def Commit(Directory, Revision):
    return Git(Directory, "rev-parse", "--verify", "--end-of-options", Revision + "^{commit}").decode("ascii").strip()


def CheckPath(Name, Application):
    Path = pathlib.PurePosixPath(Name)
    if not Name or Path.is_absolute() or ".." in Path.parts or "\\" in Name:
        raise ValueError(f"Unsafe archive path: {Name}")
    if ".git" in Path.parts:
        raise ValueError(f"Git metadata cannot enter the source archive: {Name}")
    if Path.name == "local.properties" or Path.suffix.lower() in (".jks", ".keystore", ".p12", ".pfx", ".pyc", ".pyo"):
        raise ValueError(f"Local configuration, signing key or bytecode cannot enter the source archive: {Name}")
    if Application:
        if any(Part in (".gradle", ".cxx", ".buildtools", "__pycache__", "node_modules", "dist", "build", "logs", "device-evidence", "phone-evidence", "runtime-evidence") for Part in Path.parts[:-1]):
            raise ValueError(f"Build cache or device evidence cannot enter the source archive: {Name}")
        if len(Path.parts) > 1 and Path.parts[0] == "native" and Path.parts[1].startswith("build"):
            raise ValueError(f"Native build cache cannot enter the source archive: {Name}")
    return Path.as_posix()


def AddEntry(Entries, Name, Contents, Mode):
    if Name in Entries:
        if stat.S_ISDIR(Mode) and stat.S_ISDIR(Entries[Name][1]):
            return
        raise ValueError(f"Duplicate archive entry: {Name}")
    Entries[Name] = (Contents, Mode)


def AddArchive(Entries, Directory, Revision, Prefix):
    ArchiveBytes = Git(Directory, "archive", "--format=tar", Revision)
    Count = 0
    with tarfile.open(fileobj=io.BytesIO(ArchiveBytes), mode="r:") as Archive:
        for Member in Archive:
            Name = CheckPath(Member.name, not Prefix)
            Destination = Prefix + Name
            if Member.isdir():
                AddEntry(Entries, Destination.rstrip("/") + "/", b"", stat.S_IFDIR | (Member.mode & 0o777))
                continue
            if Member.issym():
                AddEntry(Entries, Destination, Member.linkname.encode("utf-8"), stat.S_IFLNK | (Member.mode & 0o777))
            elif Member.isfile():
                Stream = Archive.extractfile(Member)
                if Stream is None:
                    raise ValueError(f"Cannot read source file: {Member.name}")
                Contents = Stream.read()
                if any(Line in (b"-----BEGIN PRIVATE KEY-----", b"-----BEGIN RSA PRIVATE KEY-----", b"-----BEGIN OPENSSH PRIVATE KEY-----", b"-----BEGIN EC PRIVATE KEY-----") for Line in Contents.splitlines()):
                    raise ValueError(f"Private key material cannot enter the source archive: {Destination}")
                AddEntry(Entries, Destination, Contents, stat.S_IFREG | (Member.mode & 0o777))
            else:
                raise ValueError(f"Unsupported archive entry type: {Member.name}")
            Count += 1
    return Count


def Main():
    Parser = argparse.ArgumentParser(description="Package an exact release commit with pinned native dependency sources, without downloading anything.")
    Parser.add_argument("--revision", required=True)
    Parser.add_argument("--output", required=True, type=pathlib.Path)
    Arguments = Parser.parse_args()
    Revision = Commit(Root, Arguments.revision)
    Epoch = int(Git(Root, "show", "-s", "--format=%ct", Revision).decode("ascii").strip())
    Timestamp = datetime.datetime.fromtimestamp(Epoch, datetime.timezone.utc)
    if Timestamp.year < 1980 or Timestamp.year > 2107:
        raise ValueError("Release commit date is outside the ZIP timestamp range")
    Date = (Timestamp.year, Timestamp.month, Timestamp.day, Timestamp.hour, Timestamp.minute, Timestamp.second - Timestamp.second % 2)
    Entries = {}
    ApplicationFiles = AddArchive(Entries, Root, Revision, "")
    SourceEntries = []
    for Name, Checkout, Destination, PinnedCommit, Url in Dependencies:
        Directory = Root / "native/dependencies" / Checkout
        Actual = Commit(Directory, PinnedCommit)
        if Actual != PinnedCommit:
            raise ValueError(f"Unexpected commit for {Name}: {Actual}")
        Prefix = "third-party-sources/" + Destination + "/"
        Count = AddArchive(Entries, Directory, Actual, Prefix)
        SourceEntries.append({"name": Name, "repository": Url, "commit": Actual, "archive_path": Prefix, "files": Count})
    Required = ("native/CMakeLists.txt", "native/Source/Module.cpp", "native/Source/Runtime.cpp", "native/patches/DobbyAndroid.patch", "hook/src/main/java/io/github/deviceidchanger/hook/Bridge.java", "manager/build.gradle.kts", "module-native/common.sh", "scripts/build.ps1", "licenses/index.json", "THIRD_PARTY_NOTICES.txt")
    Missing = [Name for Name in Required if Name not in Entries]
    Manifest = {"schema_version": 1, "repository": "https://github.com/PronabLua/DeviceiDChanger-OSS", "release_commit": Revision, "release_tree": Git(Root, "rev-parse", Revision + "^{tree}").decode("ascii").strip(), "application_files": ApplicationFiles, "application_source_complete": not Missing, "missing_application_files": Missing, "dependencies": SourceEntries, "dobby_patch": "native/patches/DobbyAndroid.patch", "libcxx_full": {"artifact": "org.lsposed.libcxx:libcxx-full:30.0.16248370", "url": "https://repo.maven.apache.org/maven2/org/lsposed/libcxx/libcxx-full/30.0.16248370/libcxx-full-30.0.16248370.aar", "sha256": "bbe93ec17cf7cbe3d6d7dd305a645b4b61d7afc09dc048dc7db53149efb65e6e", "included": False}}
    Readme = f"""DeviceID Changer corresponding source attachment

Release commit: {Revision}

The archive root contains the exact Git archive of the named release commit.
third-party-sources/ contains pristine Git archives of the pinned native
dependencies, including the nested DexBuilder and Parallel Hashmap submodules.
SOURCE_RELEASE_MANIFEST.json records their complete commit identifiers.

These dependency copies provide source and audit material. The normal build
fetches the Git revisions and Maven artifact pinned in
native/cmake/Dependencies.cmake; this archive is not an offline build environment.
The SDK, NDK, CMake, JDK and Gradle dependencies described in README.MD are still
needed. No ROM, emulator image or full AOSP tree is included or downloaded by
this source packaging script.

Do not copy these Git-metadata-free trees directly into native/dependencies/:
the current build expects real Git checkouts there. Run the normal build to
populate those checkouts, then edit the desired dependency source in place.
Keep its HEAD at the pinned commit; the build accepts working-tree source edits.
You may also copy modified files from third-party-sources/ into those initialized
checkouts. The included application source can be modified and rebuilt to relink
the module with modified LSPlant and DexBuilder libraries.

The Dobby tree is pristine at its recorded commit. The project modification is
supplied separately in native/patches/DobbyAndroid.patch and applied during
CMake configuration. Preserve that patch and all dependency license notices.

The pinned libcxx-full runtime archive is downloaded by the normal build and
verified against the SHA256 in the manifest. It is not embedded here. Its
publisher source and license information are recorded in THIRD_PARTY_NOTICES.txt.

Build caches, local Git metadata, signing keys and device evidence are excluded.
The release APK is debug-signed; modified APKs use the builder's own debug key.
Runtime validation is limited to the device and read paths described by the
release notes; it does not establish universal Android 10 through 17 support.
"""
    if Missing:
        Readme = "STRUCTURAL TEST ARCHIVE: the selected commit lacks current application sources. Do not publish this archive as corresponding source.\n\n" + Readme
    AddEntry(Entries, "SOURCE_RELEASE_MANIFEST.json", (json.dumps(Manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8"), stat.S_IFREG | 0o644)
    AddEntry(Entries, "SOURCE_RELEASE_README.txt", Readme.encode("utf-8"), stat.S_IFREG | 0o644)
    Output = Arguments.output.resolve()
    Output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(Output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as Archive:
        for Name, (Contents, Mode) in sorted(Entries.items()):
            Entry = zipfile.ZipInfo(Name, Date)
            Entry.create_system = 3
            Entry.external_attr = Mode << 16
            if stat.S_ISDIR(Mode):
                Entry.external_attr |= 0x10
            Entry.compress_type = zipfile.ZIP_DEFLATED
            Archive.writestr(Entry, Contents, compresslevel=9)
    Digest = hashlib.sha256(Output.read_bytes()).hexdigest()
    print(f"Created {Output}")
    print(f"SHA256 {Digest}")
    print(f"Application source complete: {not Missing}; dependency trees: {len(SourceEntries)}")
    if Missing:
        print("WARNING: structural test only; selected revision lacks current application source files.")


if __name__ == "__main__":
    Main()
