"""Build the standalone Lunar Dread mod from local, user-provided dependencies.

This repository never downloads or redistributes third-party mod JARs.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parent
VERSION = "0.11.4"
JAR_NAME = f"lunar-dread-{VERSION}.jar"
DEPENDENCIES = {
    "Enhanced Celestials": "enhanced-celestials",
    "Stellar View": "stellar.view",
    "CorgiLib": "corgilib",
    "Data Anchor": "data_anchor",
    "The Hordes": "the-hordes",
    "Zombies Break & Build": "zombiesbreak&build",
}


def required_dir(name: str) -> Path:
    value = os.environ.get(name)
    if not value or not Path(value).is_dir():
        raise SystemExit(f"Set {name} to an existing directory")
    return Path(value)


def main() -> None:
    server_libraries = required_dir("LUNAR_DREAD_SERVER_LIBRARIES")
    client_libraries = required_dir("LUNAR_DREAD_MINECRAFT_LIBRARIES")
    dependency_dir = required_dir("LUNAR_DREAD_DEPENDENCY_DIR")
    java_home = os.environ.get("JAVA_HOME")
    javac = (Path(java_home) / "bin" / ("javac.exe" if os.name == "nt" else "javac")) if java_home else shutil.which("javac")
    if not javac or not Path(javac).is_file():
        raise SystemExit("Java 21 JDK is required: set JAVA_HOME or put javac on PATH")

    server_jars = list(server_libraries.rglob("*.jar"))
    server_jars.sort(key=lambda jar: ("neoforge-21.1.265-server.jar" not in jar.name, str(jar)))
    if not server_jars:
        raise SystemExit("No NeoForge server libraries found")
    client_srg = list((client_libraries / "net/minecraft/client/1.21.1-20240808.144430").glob("*-srg.jar"))
    client_neoforge = list((client_libraries / "net/neoforged/neoforge/21.1.265").glob("*-client.jar"))
    if not client_srg or not client_neoforge:
        raise SystemExit("Expected Minecraft 1.21.1 and NeoForge 21.1.265 client libraries")

    available = list(dependency_dir.glob("*.jar"))
    selected = {}
    for label, fragment in DEPENDENCIES.items():
        matches = [jar for jar in available if fragment in jar.name.lower()]
        if len(matches) != 1:
            raise SystemExit(f"Expected one {label} JAR in {dependency_dir}; found {len(matches)}")
        selected[label] = matches[0]

    build_dir = ROOT / "build"
    classes = build_dir / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    zbb = selected["Zombies Break & Build"]
    eventbus = build_dir / "deps" / "eventbus-java-3.3.1.jar"
    eventbus.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zbb) as archive:
        eventbus.write_bytes(archive.read("META-INF/jarjar/eventbus-java-3.3.1.jar"))

    jars = server_jars + client_srg + client_neoforge
    jars += list((client_libraries / "org/lwjgl").rglob("*.jar"))
    jars += list(selected.values()) + [eventbus]
    sources = sorted((ROOT / "src").rglob("*.java"))
    if not sources:
        raise SystemExit("No Java source files found under src/")
    arguments = ["--release", "21", "-encoding", "UTF-8", "-proc:none", "-classpath",
                 os.pathsep.join(map(str, jars)), "-d", str(classes), *map(str, sources)]
    argument_file = build_dir / "javac.args"
    argument_file.write_text("\n".join('"' + argument.replace("\\", "/") + '"' for argument in arguments), encoding="utf-8")
    subprocess.run([str(javac), "@" + str(argument_file)], check=True)

    output = ROOT / "dist" / JAR_NAME
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for source in sorted(classes.rglob("*.class")):
            archive.write(source, source.relative_to(classes).as_posix())
        archive.write(ROOT / "META-INF/neoforge.mods.toml", "META-INF/neoforge.mods.toml")
        archive.write(ROOT / "LICENSE", "LICENSE")
        archive.write(ROOT / "README.md", "README.md")
        for resource in sorted((ROOT / "resources").rglob("*")):
            if resource.is_file():
                archive.write(resource, resource.relative_to(ROOT / "resources").as_posix())
    print(f"Built {output} ({len(sources)} Java sources)")
    print("SHA-512", hashlib.sha512(output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
