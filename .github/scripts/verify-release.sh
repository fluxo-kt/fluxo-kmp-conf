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

plugin_url="https://plugins.gradle.org/plugin/io.github.fluxo-kt.fluxo-kmp-conf/${catalog_version}"
plugin_status="$(curl -sS -o /dev/null -w '%{http_code}' "${plugin_url}" || true)"
[[ "${plugin_status}" != "200" ]] ||
  fail "Gradle Plugin Portal already has io.github.fluxo-kt.fluxo-kmp-conf ${catalog_version}"
[[ "${plugin_status}" == "400" || "${plugin_status}" == "404" ]] ||
  fail "Could not verify Gradle Plugin Portal status for ${catalog_version} (${plugin_status})"

declare -a central_artifacts=(
  "io.github.fluxo-kt|fluxo-kmp-conf|https://repo1.maven.org/maven2/io/github/fluxo-kt/fluxo-kmp-conf/${catalog_version}/fluxo-kmp-conf-${catalog_version}.pom"
  "io.github.fluxo-kt.fluxo-kmp-conf|io.github.fluxo-kt.fluxo-kmp-conf.gradle.plugin|https://repo1.maven.org/maven2/io/github/fluxo-kt/fluxo-kmp-conf/io.github.fluxo-kt.fluxo-kmp-conf.gradle.plugin/${catalog_version}/io.github.fluxo-kt.fluxo-kmp-conf.gradle.plugin-${catalog_version}.pom"
)

for artifact in "${central_artifacts[@]}"; do
  IFS='|' read -r group artifact_id central_url <<< "${artifact}"
  central_status="$(curl -sS -o /dev/null -w '%{http_code}' "${central_url}" || true)"
  [[ "${central_status}" != "200" ]] ||
    fail "Maven Central already has ${group}:${artifact_id}:${catalog_version}"
  [[ "${central_status}" == "404" ]] ||
    fail "Could not verify Maven Central status for ${group}:${artifact_id}:${catalog_version} (${central_status})"
done

echo "version=${catalog_version}" >> "${GITHUB_OUTPUT}"
echo "tag=${GITHUB_REF_NAME}" >> "${GITHUB_OUTPUT}"
