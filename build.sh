#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Build Raito.
#   ./build.sh test      compile and run the host-side tests (needs a JDK only)
#   ./build.sh           tests, then build/raito.jar with DEX inside (needs d8)
#   ./build.sh dist      tests, jar, then build/dist/: raito-<version>.tar.gz, raito-<version>.jar, SHA256SUMS
#   ./build.sh version   print the version from Main.java
#
# Tools: JAVA_HOME (JDK 11+; otherwise javac/java on PATH), and one of
#   D8=/path/to/build-tools/<ver>/d8, R8_JAR=/path/to/r8.jar, or ANDROID_HOME with build-tools.
set -eu

cd "$(dirname "$0")"
OUT=${OUT_DIR:-build}
MIN_API=26
ARTIFACT="$OUT/raito.jar"
MAIN_SOURCE=src/main/java/io/github/hallucigenia/raito/Main.java

tool() {
    if [ -n "${JAVA_HOME:-}" ]; then
        printf '%s/bin/%s\n' "$JAVA_HOME" "$1"
    else
        command -v "$1" || { echo "missing $1: set JAVA_HOME or put a JDK on PATH" >&2; exit 1; }
    fi
}

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

version() {
    v=$(sed -n 's/.*static final String VERSION = "\([^"]*\)";.*/\1/p' "$MAIN_SOURCE")
    [ -n "$v" ] || { echo "cannot read VERSION from $MAIN_SOURCE" >&2; exit 1; }
    printf '%s\n' "$v"
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
    sha256 "$ARTIFACT"
}

# Archive without local owner names or macOS metadata, so a local dist leaks nothing about the builder.
archive() {
    if tar --version 2>/dev/null | grep -q 'GNU tar'; then
        tar -C "$1" --owner=0 --group=0 --numeric-owner -cf - "$2"
    else
        COPYFILE_DISABLE=1 tar -C "$1" --uid 0 --gid 0 --uname '' --gname '' --no-xattrs -cf - "$2"
    fi
}

dist() {
    v=$(version)
    name="raito-$v"
    dist_dir="$OUT/dist"
    stage="$dist_dir/$name"
    rm -rf "$dist_dir"
    mkdir -p "$stage/bin" "$stage/device" "$stage/config"
    cp "$ARTIFACT" "$stage/raito.jar"
    cp bin/raito "$stage/bin/raito"
    cp device/raito.sh "$stage/device/raito.sh"
    cp config/raito.conf.example "$stage/config/raito.conf.example"
    cp README.md LICENSE NOTICE "$stage/"
    chmod 755 "$stage/bin/raito" "$stage/device/raito.sh"
    chmod 644 "$stage/raito.jar" "$stage/config/raito.conf.example" "$stage/README.md" "$stage/LICENSE" "$stage/NOTICE"
    archive "$dist_dir" "$name" | gzip -n >"$dist_dir/$name.tar.gz"
    cp "$ARTIFACT" "$dist_dir/$name.jar"
    rm -rf "$stage"
    (cd "$dist_dir" && sha256 "$name.tar.gz" "$name.jar" >SHA256SUMS && cat SHA256SUMS)
}

case "${1:-all}" in
    test) compile_and_test ;;
    all) compile_and_test && dex ;;
    dist) compile_and_test && dex && dist ;;
    version) version ;;
    *) echo 'Usage: ./build.sh [test|dist|version]' >&2; exit 2 ;;
esac
