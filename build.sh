#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Build Raito.
#   ./build.sh test   compile and run the host-side tests (needs a JDK only)
#   ./build.sh        tests, then build/raito.jar with DEX inside (needs d8)
#
# Tools: JAVA_HOME (JDK 11+; otherwise javac/java on PATH), and one of
#   D8=/path/to/build-tools/<ver>/d8, R8_JAR=/path/to/r8.jar, or ANDROID_HOME with build-tools.
set -eu

cd "$(dirname "$0")"
OUT=${OUT_DIR:-build}
MIN_API=26
ARTIFACT="$OUT/raito.jar"

tool() {
    if [ -n "${JAVA_HOME:-}" ]; then
        printf '%s/bin/%s\n' "$JAVA_HOME" "$1"
    else
        command -v "$1" || { echo "missing $1: set JAVA_HOME or put a JDK on PATH" >&2; exit 1; }
    fi
}

compile_and_test() {
    rm -rf "$OUT/classes" "$OUT/test-classes"
    mkdir -p "$OUT/classes" "$OUT/test-classes"
    find src/main/java -name '*.java' >"$OUT/main-sources.txt"
    find src/test/java -name '*.java' >"$OUT/test-sources.txt"
    "$(tool javac)" --release 8 -Xlint:all,-options -Werror -encoding UTF-8 \
        -d "$OUT/classes" @"$OUT/main-sources.txt"
    "$(tool javac)" --release 8 -Xlint:all,-options -Werror -encoding UTF-8 \
        -cp "$OUT/classes" -d "$OUT/test-classes" @"$OUT/test-sources.txt"
    tests=$(sed -n 's#^src/test/java/\(.*Test\)\.java$#\1#p' "$OUT/test-sources.txt" | tr / . | sort)
    # shellcheck disable=SC2086 # one argument per test class
    "$(tool java)" -cp "$OUT/classes:$OUT/test-classes" io.github.hallucigenia.raito.TestRunner $tests
}

d8() {
    if [ -n "${D8:-}" ]; then
        "$D8" "$@"
    elif [ -n "${R8_JAR:-}" ]; then
        "$(tool java)" -cp "$R8_JAR" com.android.tools.r8.D8 "$@"
    else
        sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
        latest=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; 2>/dev/null \
            | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
        if [ -z "$sdk" ] || [ -z "$latest" ]; then
            echo 'missing d8: set D8, R8_JAR, or ANDROID_HOME with build-tools installed' >&2
            exit 1
        fi
        "$sdk/build-tools/$latest/d8" "$@"
    fi
}

dex() {
    rm -f "$OUT/classes.jar" "$ARTIFACT"
    "$(tool jar)" cf "$OUT/classes.jar" -C "$OUT/classes" .
    d8 --release --min-api "$MIN_API" --no-desugaring --output "$ARTIFACT" "$OUT/classes.jar"
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$ARTIFACT"; else shasum -a 256 "$ARTIFACT"; fi
}

case "${1:-all}" in
    test) compile_and_test ;;
    all) compile_and_test && dex ;;
    *) echo 'Usage: ./build.sh [test]' >&2; exit 2 ;;
esac
