"""Build the translator, then instantiate its output using the native 26.3 decoder."""
import argparse
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

from build import ROOT, classpath, compile_sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("via", "snapshot_libraries", "vanilla_server_26_3", "vanilla_libraries_26_3"):
        parser.add_argument(name, type=Path)
    parser.add_argument("extra_fixture", nargs="*", type=Path)
    args = parser.parse_args()
    fixtures = ROOT / "tests/commands"
    bridge = ROOT / "build/FrontierBridge.jar"
    import sys
    subprocess.run([sys.executable, str(ROOT / "build.py"), str(args.via), str(args.snapshot_libraries)], check=True)
    with TemporaryDirectory() as tmp:
        native = os.pathsep.join([tmp, classpath(args.vanilla_server_26_3, args.vanilla_libraries_26_3)])
        translation = os.pathsep.join([tmp, str(bridge), classpath(args.via, args.snapshot_libraries)])
        compile_sources([next((ROOT / "src/test").rglob("Snapshot8CommandCodecTest.java"))], translation, tmp)
        compile_sources([fixtures / "ValidateCommandRoot26_3.java"], native, tmp)
        output = Path(tmp) / "translated.json"
        for fixture in [fixtures / "template-pool.json", fixtures / "server-registries.json", *args.extra_fixture]:
            subprocess.run(["java", "-cp", translation, "net.lumalyte.frontier.bridge.Snapshot8CommandCodecTest",
                            str(fixture), str(output)], check=True)
            subprocess.run(["java", "-cp", native, "ValidateCommandRoot26_3", str(output)], check=True)


if __name__ == "__main__":
    main()
