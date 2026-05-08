#!/usr/bin/env bash
set -euo pipefail

APP="/mnt/c/Users/Student/Documents/X88 FIrmware/tools/frontpanel-test"
SDK="${HOME}/android-sdk"
BT="${SDK}/build-tools/35.0.0"

rm -rf "${APP}/build/classes" "${APP}/build/dex"
mkdir -p "${APP}/build/classes" "${APP}/build/dex"

mapfile -d '' JAVA_SOURCES < <(find "${APP}/src" -name '*.java' -print0)

javac -source 8 -target 8 \
    -bootclasspath "${SDK}/platforms/android-35/android.jar" \
    -d "${APP}/build/classes" \
"${JAVA_SOURCES[@]}"

mapfile -d '' CLASS_FILES < <(find "${APP}/build/classes" -name '*.class' -print0)

"${BT}/d8" --min-api 26 \
    --lib "${SDK}/platforms/android-35/android.jar" \
    --output "${APP}/build/dex" \
    "${CLASS_FILES[@]}"

(cd "${APP}/build/dex" && jar cf ../x88-hw.jar classes.dex)
ls -l "${APP}/build/x88-hw.jar"
