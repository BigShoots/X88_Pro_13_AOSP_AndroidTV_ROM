#!/usr/bin/env bash
set -euo pipefail

ROOT="/mnt/c/Users/Student/Documents/X88 FIrmware"
PATCH="${ROOT}/tools/tvsettings-rk-patch"
SDK="${HOME}/android-sdk"
BT="${SDK}/build-tools/35.0.0"
OUT="${PATCH}/build"
APKTOOL="${ROOT}/tools/apktool.jar"
JAVA_CMD="java"

rm -rf "${OUT}"
mkdir -p "${OUT}/stub-classes" "${OUT}/classes" "${OUT}/dex" "${OUT}/dummy" "${OUT}/smali"

mapfile -d '' STUB_SOURCES < <(find "${PATCH}/stubs" -name '*.java' -print0)
javac -source 8 -target 8 \
    -bootclasspath "${SDK}/platforms/android-35/android.jar" \
    -d "${OUT}/stub-classes" \
    "${STUB_SOURCES[@]}"

mapfile -d '' SOURCES < <(find "${PATCH}/src" -name '*.java' -print0)
javac -source 8 -target 8 \
    -bootclasspath "${SDK}/platforms/android-35/android.jar" \
    -classpath "${OUT}/stub-classes" \
    -d "${OUT}/classes" \
    "${SOURCES[@]}"

mapfile -d '' CLASS_FILES < <(find "${OUT}/classes" -name '*.class' -print0)
"${BT}/d8" --min-api 33 \
    --lib "${SDK}/platforms/android-35/android.jar" \
    --output "${OUT}/dex" \
    "${CLASS_FILES[@]}"

cp "${OUT}/dex/classes.dex" "${OUT}/dummy/classes.dex"
(cd "${OUT}/dummy" && zip -q "${OUT}/dummy.apk" classes.dex)
rm -rf "${OUT}/dummy-decoded"
"${JAVA_CMD}" -jar "${APKTOOL}" d -f -r "${OUT}/dummy.apk" -o "${OUT}/dummy-decoded"
cp -a "${OUT}/dummy-decoded/smali/." "${OUT}/smali/"
find "${OUT}/smali" -type f -print
