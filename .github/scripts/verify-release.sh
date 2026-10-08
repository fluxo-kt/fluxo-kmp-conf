#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*"
  exit 1
}

catalog_version="$(
  awk -F '"' '/^version[[:space:]]*=/ { print $2; exit }' gradle/libs.versions.toml
)"

[[ -n "${catalog_version}" ]] || fail "Could not read release version from gradle/libs.versions.toml"
[[ "${GITHUB_REF_TYPE:-}" == "tag" ]] || fail "Release workflow must run from a tag"
[[ "${GITHUB_REF_NAME:-}" == "v${catalog_version}" ]] ||
  fail "Tag ${GITHUB_REF_NAME:-<unset>} does not match catalog version ${catalog_version}"

tag_commit="$(git rev-list -n 1 "${GITHUB_REF_NAME}" 2>/dev/null)" ||
  fail "Tag ${GITHUB_REF_NAME} is not present in the checkout"
[[ "${tag_commit}" == "${GITHUB_SHA}" ]] ||
  fail "Tag ${GITHUB_REF_NAME} points to ${tag_commit}, but workflow SHA is ${GITHUB_SHA}"

grep -Fq "## [${catalog_version}]" CHANGELOG.md ||
  fail "CHANGELOG.md has no section for ${catalog_version}"

if gh release view "${GITHUB_REF_NAME}" >/dev/null 2>&1; then
  fail "GitHub release ${GITHUB_REF_NAME} already exists"
fi

# A version already on a repository is not an error but a finished step: each publish step
# skips its repository when it already has the version, so a run that failed after publishing
# to one of them can be rerun (from a moved tag) and finishes the other.
plugin_url="https://plugins.gradle.org/plugin/io.github.fluxo-kt.fluxo-kmp-conf/${catalog_version}"
plugin_status="$(curl -sS -o /dev/null -w '%{http_code}' "${plugin_url}" || true)"
portal_published=false
case "${plugin_status}" in
  200) portal_published=true ;;
  400 | 404) ;;
  *) fail "Could not verify Gradle Plugin Portal status for ${catalog_version} (${plugin_status})" ;;
esac

declare -a central_artifacts=(
  "io.github.fluxo-kt|fluxo-kmp-conf|https://repo1.maven.org/maven2/io/github/fluxo-kt/fluxo-kmp-conf/${catalog_version}/fluxo-kmp-conf-${catalog_version}.pom"
  "io.github.fluxo-kt.fluxo-kmp-conf|io.github.fluxo-kt.fluxo-kmp-conf.gradle.plugin|https://repo1.maven.org/maven2/io/github/fluxo-kt/fluxo-kmp-conf/io.github.fluxo-kt.fluxo-kmp-conf.gradle.plugin/${catalog_version}/io.github.fluxo-kt.fluxo-kmp-conf.gradle.plugin-${catalog_version}.pom"
)

# One Central deployment publishes every artifact at once, so all present means published and
# a mix means it is still propagating: fail rather than publish the version a second time.
central_found=0
for artifact_spec in "${central_artifacts[@]}"; do
  IFS='|' read -r group artifact_id central_url <<< "${artifact_spec}"
  central_status="$(curl -sS -o /dev/null -w '%{http_code}' "${central_url}" || true)"
  case "${central_status}" in
    200) central_found=$((central_found + 1)) ;;
    404) ;;
    *) fail "Could not verify Maven Central status for ${group}:${artifact_id}:${catalog_version} (${central_status})" ;;
  esac
done
central_published=false
case "${central_found}" in
  0) ;;
  "${#central_artifacts[@]}") central_published=true ;;
  *) fail "Maven Central has only some ${catalog_version} artifacts (still propagating); rerun later" ;;
esac

# Single source of truth for "is this a pre-release". Semver places the marker
# after a hyphen (e.g. v0.15.0-alpha01); anchoring on `-` avoids false positives
# like a hypothetical v1.0-march matching "rc". Consumed by gh_release to mark
# the GitHub release as a pre-release.
prerelease=false
if [[ "${GITHUB_REF_NAME}" =~ -(alpha|beta|rc) ]]; then
  prerelease=true
fi

{
  echo "version=${catalog_version}"
  echo "tag=${GITHUB_REF_NAME}"
  echo "prerelease=${prerelease}"
  echo "central_published=${central_published}"
  echo "portal_published=${portal_published}"
} >> "${GITHUB_OUTPUT}"
