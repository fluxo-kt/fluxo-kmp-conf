#!/usr/bin/env bash
# Prints the newest Kotlin, Gradle, AGP, Compose Multiplatform and KSP releases, one
# `name=version` per line, for the weekly run against the newest upstream releases.
#
# Usage: resolve-newest-upstream.sh <stable|pre>
#   stable — newest final releases only.
#   pre    — also Beta and RC releases, when newer than the newest final one. Dev, alpha and
#            snapshot builds are never picked: their own bugs would turn the run red for
#            reasons that are not this plugin's.
set -euo pipefail

channel="${1:?usage: $0 <stable|pre>}"
case "$channel" in stable | pre) ;; *) echo "unknown channel: $channel" >&2; exit 2 ;; esac

# Newest of the given versions. A prerelease sorts before its final release: `-` becomes `~`,
# which `sort -V` orders before anything, so 2.5.0-RC < 2.5.0.
newest() {
  sed -E 's/-/~/' | sort -V | tail -1 | sed -E 's/~/-/'
}

# Versions from a Maven repository's metadata, filtered to the channel.
maven_versions() {
  curl -fsSL "$1" | grep -o '<version>[^<]*</version>' | sed -E 's#</?version>##g' |
    if [ "$channel" = stable ]; then
      grep -E '^[0-9]+(\.[0-9]+)*$'
    else
      grep -E -i '^[0-9]+(\.[0-9]+)*(-(beta|rc)-?[0-9]*)?$'
    fi
}

central=https://repo1.maven.org/maven2
google=https://dl.google.com/android/maven2

kotlin=$(maven_versions "$central/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml" | newest)
agp=$(maven_versions "$google/com/android/tools/build/gradle/maven-metadata.xml" | newest)
compose=$(maven_versions "$central/org/jetbrains/compose/compose-gradle-plugin/maven-metadata.xml" | newest)
ksp=$(maven_versions "$central/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml" | newest)

gradle=$(curl -fsSL https://services.gradle.org/versions/current | sed -nE 's/.*"version" *: *"([^"]+)".*/\1/p')
if [ "$channel" = pre ]; then
  rc=$(curl -fsSL https://services.gradle.org/versions/release-candidate | sed -nE 's/.*"version" *: *"([^"]+)".*/\1/p')
  if [ -n "$rc" ]; then gradle=$(printf '%s\n%s\n' "$gradle" "$rc" | newest); fi
fi

for pair in "kotlin=$kotlin" "gradle=$gradle" "agp=$agp" "compose=$compose" "ksp=$ksp"; do
  # One version, digits first: anything else is a parse failure, not a version.
  [[ "${pair#*=}" =~ ^[0-9][0-9A-Za-z.-]*$ ]] || {
    echo "could not resolve ${pair%=*}: '${pair#*=}'" >&2
    exit 1
  }
  echo "$pair"
done
