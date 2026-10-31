#!/usr/bin/env python3
"""Build macOS .app and portable Windows .exe, each with a verified Temurin JRE 21."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "target" / "desktop-packaging"
LAUNCHER = "vn.edu.nhom7.quiz.client.DesktopLauncher"


def run(args, **kwargs):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), check=True, cwd=ROOT, **kwargs)


def metadata(url):
    return json.loads(subprocess.check_output(["curl", "--fail", "--silent", "--show-error", "--location", url]))


def download(url, name, digest, algorithm="sha256"):
    path = CACHE / "downloads" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or hashlib.new(algorithm, path.read_bytes()).hexdigest() != digest:
        partial = path.with_suffix(path.suffix + ".part")
        run(["curl", "--fail", "--location", "--retry", "3", "--output", partial, url])
        if hashlib.new(algorithm, partial.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Checksum mismatch: " + name)
        partial.replace(path)
    print(f"Verified {algorithm}:", name, flush=True)
    return path


def extract(archive, destination):
    if destination.exists():
        return destination
    destination.mkdir(parents=True)
    try:
        if zipfile.is_zipfile(archive):
            with zipfile.ZipFile(archive) as source:
                for item in source.infolist():
                    target = (destination / item.filename).resolve()
                    if not target.is_relative_to(destination.resolve()):
                        raise RuntimeError("Unsafe archive path")
                source.extractall(destination)
        else:
            with tarfile.open(archive) as source:
                source.extractall(destination, filter="data")
    except BaseException:
        shutil.rmtree(destination)
        raise
    return destination


def jre(system, arch):
    url = ("https://api.adoptium.net/v3/assets/latest/21/hotspot"
           f"?architecture={arch}&image_type=jre&os={system}&vendor=eclipse")
    release = metadata(url)[0]
    package = release["binary"]["package"]
    archive = download(package["link"], package["name"], package["checksum"])
    unpacked = extract(archive, CACHE / "runtimes" / archive.stem)
    binary = "javaw.exe" if system == "windows" else "java"
    home = next(p.parent.parent for p in unpacked.rglob(binary) if p.parent.name == "bin")
    return home, {"vendor": "Eclipse Temurin", "version": release["version"]["semver"],
                  "source": package["link"], "sha256": package["checksum"]}


def go_compiler():
    local = shutil.which("go")
    if local:
        return Path(local)
    system = {"Darwin": "darwin", "Linux": "linux", "Windows": "windows"}[platform.system()]
    arch = {"arm64": "arm64", "aarch64": "arm64", "x86_64": "amd64", "AMD64": "amd64"}[platform.machine()]
    release = next(r for r in metadata("https://go.dev/dl/?mode=json") if r["stable"])
    package = next(p for p in release["files"] if p["os"] == system and p["arch"] == arch and p["kind"] == "archive")
    archive = download("https://go.dev/dl/" + package["filename"], package["filename"], package["sha256"])
    unpacked = extract(archive, CACHE / "toolchains" / release["version"])
    return unpacked / "go" / "bin" / ("go.exe" if system == "windows" else "go")


def java_home():
    home = os.environ.get("JAVA_HOME")
    if platform.system() == "Darwin":
        home = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
    if not home or not (Path(home) / "bin" / ("jpackage.exe" if os.name == "nt" else "jpackage")).exists():
        raise RuntimeError("Set JAVA_HOME to a JDK 21 with jpackage.")
    return Path(home)


def dependencies(work, classifier, env, project_version, fx_version):
    app = work / classifier
    app.mkdir()
    shutil.copy2(ROOT / "target" / f"quiz-arena-{project_version}-client.jar",
                 app / f"quiz-arena-client-{project_version}.jar")
    run(["mvn", "dependency:copy-dependencies", "-DincludeScope=runtime",
         "-DexcludeGroupIds=org.openjfx,com.mysql,com.google.protobuf",
         f"-DoutputDirectory={app}"], env=env)
    for module in ("base", "graphics", "controls", "media"):
        name = f"javafx-{module}-{fx_version}-{classifier}.jar"
        url = f"https://repo.maven.apache.org/maven2/org/openjfx/javafx-{module}/{fx_version}/{name}"
        sha = subprocess.check_output(["curl", "-fsSL", url + ".sha1"], text=True).strip().split()[0]
        shutil.copy2(download(url, name, sha, "sha1"), app / name)
    return app


def instructions(destination, system, version):
    text = f"""Quiz Arena {version} — {system}

Mở Quiz Arena.app (macOS) hoặc giải nén toàn bộ ZIP rồi mở Quiz Arena.exe (Windows).
Gói đã bao gồm Java 21 và JavaFX; máy chơi không cần cài Java/Maven/MySQL.

Client cần kết nối đến server Quiz Arena 3.0 đang chạy, database schema 5.
Trong màn hình đăng nhập, nhập Host là IP/domain server và Port (mặc định 5555).
Nếu server chạy trên máy khác, dùng IP của máy đó thay cho localhost.
Ba dạng mới: Quiz của tôi → Loại câu hỏi → Sắp xếp / Ước lượng / Ghim trên ảnh.

Windows: giữ thư mục runtime và lib cùng Quiz Arena.exe.
Nhật ký Windows: %LOCALAPPDATA%\\Quiz Arena\\logs\\client.log.
Gói chỉ chứa client; server và cấu hình database không được nhúng vào client.

Java runtime: Eclipse Temurin 21 (GPLv2 with Classpath Exception), giấy phép trong runtime/legal.
JavaFX: OpenJFX 21.0.6 (GPLv2 with Classpath Exception), giấy phép trong các JAR.
Jackson: Apache License 2.0, giấy phép trong META-INF của các JAR.
Nguồn và checksum runtime được ghi trong build-info.json.
"""
    (destination / "HUONG-DAN.txt").write_text(text, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=("macos", "windows", "both"), default="both")
    parser.add_argument("--version", default="3.0.0")
    args = parser.parse_args()
    import re
    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        parser.error("Version must be x.y.z")
    home = java_home()
    env = dict(os.environ, JAVA_HOME=str(home), PATH=str(home / "bin") + os.pathsep + os.environ["PATH"])
    pom = ET.parse(ROOT / "pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    project_version = pom.findtext("m:version", namespaces=ns)
    fx_version = pom.findtext("m:properties/m:javafx.version", namespaces=ns)
    if args.platform in ("macos", "both") and platform.system() != "Darwin":
        parser.error("Build macOS .app on macOS; Windows portable can be cross-built.")
    CACHE.mkdir(parents=True, exist_ok=True)
    run(["mvn", "package", "-DskipTests"], env=env)
    platforms = ["macos", "windows"] if args.platform == "both" else [args.platform]
    with tempfile.TemporaryDirectory(prefix="stage-", dir=CACHE) as temporary:
        work = Path(temporary)
        for system in platforms:
            arch = "arm64" if system == "macos" and platform.machine() == "arm64" else "x64"
            name = f"QuizArena-{args.version}-{system}-{arch}"
            destination = ROOT / "dist" / name
            if destination.exists() or destination.with_name(destination.name + ".zip").exists():
                raise RuntimeError(f"Output already exists: {destination}. Move it aside before rebuilding.")
            output = work / name
            output.mkdir()
            classifier = ("mac-aarch64" if arch == "arm64" else "mac") if system == "macos" else "win"
            app = dependencies(work, classifier, env, project_version, fx_version)
            runtime, info = jre("mac" if system == "macos" else "windows", "aarch64" if arch == "arm64" else "x64")
            if system == "macos":
                run([home / "bin/jpackage", "--type", "app-image", "--name", "Quiz Arena",
                     "--app-version", args.version, "--vendor", "Quiz Arena - Nhom 7",
                     "--description", "Quiz Arena - Dau tri 1 doi 1",
                     "--input", app, "--main-jar", f"quiz-arena-client-{project_version}.jar",
                     "--main-class", LAUNCHER, "--runtime-image", runtime,
                     "--mac-package-identifier", "vn.edu.nhom7.quizarena", "--dest", output], env=env)
            else:
                shutil.copytree(runtime, output / "runtime")
                shutil.copytree(app, output / "lib")
                go = go_compiler()
                go_env = dict(env, GOOS="windows", GOARCH="amd64", CGO_ENABLED="0", GOTOOLCHAIN="local", GOCACHE=str(CACHE / "go-build"))
                run([go, "build", "-trimpath", "-ldflags=-s -w -H=windowsgui", "-o", output / "Quiz Arena.exe", ROOT / "scripts/windows-launcher.go"], env=go_env)
            instructions(output, system, args.version)
            (output / "build-info.json").write_text(json.dumps({"appVersion": args.version, "platform": system, "arch": arch,
                "javafxVersion": fx_version, "runtime": info}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(output, destination)
            output = destination
            archive = output.with_name(output.name + ".zip")
            if system == "macos":
                run(["ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", output, archive])
            else:
                with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
                    for path in sorted(output.rglob("*")):
                        if path.is_file():
                            zipped.write(path, path.relative_to(output.parent))
            print(f"READY: {archive} ({archive.stat().st_size / 1024 / 1024:.1f} MiB)", flush=True)


if __name__ == "__main__":
    main()
