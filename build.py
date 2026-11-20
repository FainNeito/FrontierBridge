"""Compile the bridge against a caller-supplied Snapshot 8 runtime."""
import argparse
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parent


def classpath(via, libraries):
    jars = sorted(libraries.rglob("*.jar"))
    if not via.is_file() or not jars:
        raise ValueError("Supply the patched ViaVersion JAR and populated Paper libraries directory")
    return os.pathsep.join(str(p.resolve()) for p in [via, *jars])


def compile_sources(sources, cp, output):
    args = ["--release", "25", "-encoding", "UTF-8", "-cp", cp, "-d", str(output), *map(str, sources)]
    with TemporaryDirectory() as temp:
        file = Path(temp) / "javac.args"
        file.write_text("\n".join('"' + arg.replace("\\", "/") + '"' for arg in args), encoding="utf-8")
        subprocess.run(["javac", "@" + str(file)], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("via", type=Path)
    parser.add_argument("libraries", type=Path)
    options = parser.parse_args()
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    with TemporaryDirectory(dir=build) as classes:
        compile_sources(sorted((ROOT / "src/main/java").rglob("*.java")),
                        classpath(options.via, options.libraries), classes)
        subprocess.run(["jar", "--create", "--file", str(build / "FrontierBridge.jar"),
                        "-C", classes, ".", "-C", str(ROOT / "src/main/resources"), "."], check=True)
