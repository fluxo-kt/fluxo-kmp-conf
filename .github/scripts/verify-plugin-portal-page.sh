#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*"
  exit 1
}

version="${1:-${RELEASE_VERSION:-}}"
[[ -n "$version" ]] || fail "Release version argument or RELEASE_VERSION is required."

plugin_id="io.github.fluxo-kt.fluxo-kmp-conf"
repo_url="https://github.com/fluxo-kt/fluxo-kmp-conf"
source_url="$repo_url/tree/v$version"
description="Gradle convention plugin for Kotlin Multiplatform (KMP): target-aware setup for Android, JVM, JS, Native, Compose, lint, Detekt, publishing, and CI."
tags=("kotlin" "kotlin-multiplatform" "android" "compose" "gradle-configuration" "convenience")
attempts="${PLUGIN_PORTAL_VERIFY_ATTEMPTS:-12}"
delay_seconds="${PLUGIN_PORTAL_VERIFY_DELAY_SECONDS:-10}"
[[ "$attempts" =~ ^[1-9][0-9]*$ ]] ||
  fail "PLUGIN_PORTAL_VERIFY_ATTEMPTS must be a positive integer."
[[ "$delay_seconds" =~ ^[0-9]+$ ]] ||
  fail "PLUGIN_PORTAL_VERIFY_DELAY_SECONDS must be a non-negative integer."
page="$(mktemp)"
trap 'rm -f "$page"' EXIT

plugin_url="https://plugins.gradle.org/plugin/$plugin_id/$version"
latest_url="https://plugins.gradle.org/plugin/$plugin_id"

for ((attempt = 1; attempt <= attempts; attempt += 1)); do
  curl -fsSL -H 'Cache-Control: no-cache' "$plugin_url" > "$page" || true
  if grep -Fq "Version $version" "$page" &&
    grep -Fq "$description" "$page" &&
    grep -Fq "href=\"$repo_url\"" "$page" &&
    grep -Fq "href=\"$source_url\"" "$page"; then
    break
  fi
  if ((attempt < attempts)); then
    sleep "$delay_seconds"
  fi
done

grep -Fq "Version $version" "$page" ||
  fail "Plugin Portal page does not show version $version at $plugin_url"
grep -Fq "$description" "$page" ||
  fail "Plugin Portal page does not contain the expected description."
grep -Fq "href=\"$repo_url\"" "$page" ||
  fail "Plugin Portal page does not contain expected website $repo_url"
grep -Fq "href=\"$source_url\"" "$page" ||
  fail "Plugin Portal page does not contain expected source URL $source_url"

for tag in "${tags[@]}"; do
  grep -Fq "#$tag" "$page" ||
    fail "Plugin Portal page does not contain expected tag #$tag"
done

curl -fsSL -H 'Cache-Control: no-cache' "$latest_url" > "$page"
grep -Fq "Version $version  (latest)" "$page" ||
  fail "Plugin Portal latest page does not mark $version as latest."
