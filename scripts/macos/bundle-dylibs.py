#!/usr/bin/env python3
"""Relocate a native dependency closure into one self-contained macOS directory."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess


def run(*args):
    return subprocess.check_output(args, text=True)


def dependencies(path):
    lines = run("otool", "-L", str(path)).splitlines()[1:]
    identity = run("otool", "-D", str(path)).splitlines()[1:]
    self_id = identity[0].strip() if identity else None
    return [match.group(1) for line in lines
            if (match := re.match(r"\s*(.*?) \(compatibility version ", line))
            and match.group(1) != self_id]


def digest(path):
    checksum = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.digest()


def resolve(name, source, search):
    if name.startswith("@loader_path/"):
        candidates = [source.parent / name.removeprefix("@loader_path/")]
    elif name.startswith("@rpath/"):
        suffix = name.removeprefix("@rpath/")
        candidates = [source.parent / suffix, *(directory / suffix for directory in search)]
    elif name.startswith("/"):
        candidates = [Path(name)]
    else:
        raise RuntimeError(f"Unsupported native dependency {name} in {source.name}")
    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve()
    raise RuntimeError(f"Cannot resolve {name} required by {source.name}")


def bundle(output, roots, search):
    output.mkdir(parents=True, exist_ok=True)
    queued = [(path.resolve(), path.name) for path in roots]
    staged = {}
    source_dirs = set()
    while queued:
        source, filename = queued.pop()
        if filename in staged:
            if digest(source) != digest(staged[filename]):
                raise RuntimeError(f"Conflicting native libraries named {filename}")
            continue
        staged[filename] = source
        source_dirs.add(source)
        for name in dependencies(source):
            if name.startswith(("/usr/lib/", "/System/Library/")):
                continue
            dependency = resolve(name, source, search)
            queued.append((dependency, Path(name).name))

    for filename, source in staged.items():
        target = output / filename
        shutil.copy2(source, target)
        target.chmod(0o755)
        subprocess.run(["install_name_tool", "-id", "@loader_path/" + filename, str(target)], check=True)
        for name in dependencies(source):
            if not name.startswith(("/usr/lib/", "/System/Library/")):
                subprocess.run(["install_name_tool", "-change", name,
                                "@loader_path/" + Path(name).name, str(target)], check=True)
        subprocess.run(["codesign", "--force", "--sign", "-", "--timestamp=none", str(target)], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for name in dependencies(target):
            if not name.startswith(("@loader_path/", "/usr/lib/", "/System/Library/")):
                raise RuntimeError(f"Unrelocated dependency {name} in {filename}")
    (output / "manifest.txt").write_text("".join(name + "\n" for name in sorted(staged)))
    return source_dirs


def copy_licenses(output, sources):
    licenses = output / "licenses"
    licenses.mkdir(exist_ok=True)
    for source in sources:
        parts = source.parts
        if "Cellar" not in parts:
            continue
        index = parts.index("Cellar")
        if len(parts) <= index + 2:
            continue
        package = parts[index + 1]
        root = Path(*parts[:index + 3])
        for file in root.iterdir():
            if file.is_file() and re.match(r"(?i)^(license|copying|copyright|[a-z]*gpl)", file.name):
                destination = licenses / package
                destination.mkdir(exist_ok=True)
                shutil.copy2(file, destination / file.name)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--search", type=Path, action="append", default=[])
    parser.add_argument("roots", type=Path, nargs="+")
    args = parser.parse_args()
    sources = bundle(args.output, args.roots, args.search)
    copy_licenses(args.output, sources)
    print(f"Bundled {len(list(args.output.glob('*.dylib')))} macOS native libraries")


if __name__ == "__main__":
    main()
