#!/usr/bin/env bash
# Build the exact API-specific VoxelWorlds distribution set.
set -euo pipefail
cd "$(dirname "$0")/../.."
VERSION=$(python3 -c 'import xml.etree.ElementTree as ET; p=ET.parse("pom.xml").getroot(); print(p.find("{http://maven.apache.org/POM/4.0.0}version").text)')
mkdir -p dist
build() {
  local suffix="$1"
  local api_line=''
  if [[ "$suffix" != 'legacy-1.12' ]]; then
    api_line="api-version: '$suffix'"
  fi
  mvn --batch-mode --no-transfer-progress clean package -Dplugin.api.version="$api_line" -Djar.suffix="$suffix"
  local original="target/VoxelWorlds-$VERSION-$suffix.jar"
  [[ -s "$original" ]] || { echo "Missing $original"; exit 1; }
  cp "$original" "dist/VoxelWorlds-$suffix.jar"
}
for suffix in legacy-1.12 1.13 1.14 1.15 1.16 1.17 1.18 1.19 1.20 1.21 1.22 1.23 1.24 1.25 26.1 26.2 26.3; do
  build "$suffix"
done
