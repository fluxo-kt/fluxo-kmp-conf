#!/usr/bin/env bash
set -euo pipefail

# Post-publish PROPAGATION check for the Gradle Plugin Portal page. Runs in the
# NON-GATING `verify_portal_page` job (release.yml) — it must never block release
# completion. Deliberately minimal: it asserts ONLY that the publish reached the
# live page (the version page exists and is marked `(latest)`) plus a stable
# repo-link anchor confirming it is our plugin's page.
#
# It must NOT re-assert metadata literals (description, tags, source URL). Those
# are the single source of truth on the `gradlePlugin` extension, validated BEFORE
# publish by `:plugin:verifyPluginPortalMetadata`. The Portal then renders the page
# on its own terms — it drops tags beyond its display limit (e.g. `compose`) and
# links the repo's DEFAULT branch (`.../tree/main`), never the per-version `vcsUrl`
# (`.../tree/v$version`). Hardcoding those here both duplicates the build (drifts)
# and asserts Portal rendering we do not control; doing so sank v0.15.0's first
# publish run (artifacts published, but this check false-failed and skipped
# `gh_release` + `main` promotion). Verified against the live 0.14.1 / 0.15.0 pages.

fail() {
  echo "::error::$*"
  exit 1
}

version="${1:-${RELEASE_VERSION:-}}"
[[ -n "$version" ]] || fail "Release version argument or RELEASE_VERSION is required."

plugin_id="io.github.fluxo-kt.fluxo-kmp-conf"
repo_url="https://github.com/fluxo-kt/fluxo-kmp-conf"
attempts="${PLUGIN_PORTAL_VERIFY_ATTEMPTS:-12}"
delay_seconds="${PLUGIN_PORTAL_VERIFY_DELAY_SECONDS:-10}"
[[ "$attempts" =~ ^[1-9][0-9]*$ ]] ||
  fail "PLUGIN_PORTAL_VERIFY_ATTEMPTS must be a positive integer."
[[ "$delay_seconds" =~ ^[0-9]+$ ]] ||
  fail "PLUGIN_PORTAL_VERIFY_DELAY_SECONDS must be a non-negative integer."
page="$(mktemp)"
latest_page="$(mktemp)"
trap 'rm -f "$page" "$latest_page"' EXIT

plugin_url="https://plugins.gradle.org/plugin/$plugin_id/$version"
latest_url="https://plugins.gradle.org/plugin/$plugin_id"

# Match "Version X (latest)" tolerant of the Portal's exact whitespace (it renders
# two spaces today) by collapsing runs to one — we control propagation, not layout.
is_latest() { tr -s '[:space:]' ' ' < "$latest_page" | grep -Fq "Version $version (latest)"; }

# The Portal indexes asynchronously and the `latest` pointer can lag the version
# page, so BOTH must be inside the loop — anything checked after it loses retry
# cover and re-opens the propagation false-fail this job exists to absorb.
for ((attempt = 1; attempt <= attempts; attempt += 1)); do
  curl -fsSL -H 'Cache-Control: no-cache' "$plugin_url" > "$page" || true
  curl -fsSL -H 'Cache-Control: no-cache' "$latest_url" > "$latest_page" || true
  if grep -Fq "Version $version" "$page" &&
    grep -Fq "href=\"$repo_url\"" "$page" && is_latest; then
    break
  fi
  if ((attempt < attempts)); then
    sleep "$delay_seconds"
  fi
done

grep -Fq "Version $version" "$page" ||
  fail "Plugin Portal page does not show version $version at $plugin_url"
grep -Fq "href=\"$repo_url\"" "$page" ||
  fail "Plugin Portal page does not link back to $repo_url"
is_latest ||
  fail "Plugin Portal latest page does not mark $version as latest."
