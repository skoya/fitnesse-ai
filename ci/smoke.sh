#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="build/test-results/fitnesse-smoke"

./gradlew classes copyRuntimeLibs
rm -rf "$OUT_DIR"

java -cp build/classes/java/main:build/resources/main:lib/* \
  fitnesseMain.FitNesseTestMain \
  --root . \
  --suite SmokeSuite \
  --format junit,html,json \
  --out "$OUT_DIR"

if ! find "$OUT_DIR" -name "*.xml" -print -quit | grep -q .; then
  echo "Smoke test did not produce JUnit XML output."
  exit 1
fi
