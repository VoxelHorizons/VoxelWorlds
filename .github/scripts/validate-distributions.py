#!/usr/bin/env python3
"""Fail closed if a VoxelWorlds distribution has the wrong embedded metadata."""
import pathlib
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
NS = "{http://maven.apache.org/POM/4.0.0}"
version = ET.parse(ROOT / "pom.xml").getroot().find(NS + "version").text
suffixes = ("legacy-1.12",) + tuple(f"1.{i}" for i in range(13, 26)) + ("26.1", "26.2", "26.3")
expected_files = {f"VoxelWorlds-{suffix}.jar" for suffix in suffixes}
actual_files = {p.name for p in (ROOT / "dist").glob("*.jar")}
if actual_files != expected_files:
    raise SystemExit(f"Unexpected distributions: missing {expected_files-actual_files}, extra {actual_files-expected_files}")

def prop(text, name):
    match = re.search(r"^\s*" + re.escape(name) + r":\s*(.*?)\s*$", text, re.MULTILINE)
    return match.group(1).strip("'\"") if match else None

for suffix in suffixes:
    path = ROOT / "dist" / f"VoxelWorlds-{suffix}.jar"
    with zipfile.ZipFile(path) as jar:
        metadata = jar.read("plugin.yml").decode("utf-8")
    if prop(metadata, "name") != "VoxelWorlds" or prop(metadata, "version") != version:
        raise SystemExit(f"{path}: plugin name/version mismatch")
    api = None if suffix == "legacy-1.12" else suffix
    if prop(metadata, "api-version") != api:
        raise SystemExit(f"{path}: api-version must be {api!r}")
    print(f"{path.name}: valid (version={version}, api-version={api or 'legacy'})")
