"""Resolve the public CI inputs and unpack Mojang's 26.3 server bundle."""
import hashlib
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "build/ci-inputs"


def resolve(entry):
    path = DEST / entry["file"]
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
        subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--silent", "--show-error",
                        "--output", str(path), entry["url"]], check=True)
        if hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
            raise ValueError(f"SHA-256 mismatch for {path.name}")


if __name__ == "__main__":
    for dependency in json.loads(Path(__file__).with_name("inputs.json").read_text()):
        resolve(dependency)
    with ZipFile(DEST / "server-bundle.jar") as bundle:
        for group in ("versions", "libraries"):
            entries = bundle.read(f"META-INF/{group}.list").decode().splitlines()
            for entry in entries:
                digest, identifier, name = entry.split("\t")
                if group == "versions" and identifier != "26.3":
                    raise ValueError(f"Unexpected server version {identifier}")
                data = bundle.read(f"META-INF/{group}/{name}")
                if hashlib.sha256(data).hexdigest() != digest:
                    raise ValueError(f"Corrupt bundled JAR {name}")
                target = DEST / "server.jar" if group == "versions" else DEST / "compile/vanilla" / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
