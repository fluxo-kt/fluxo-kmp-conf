#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*"
  exit 1
}

if [[ -z "${GH_TOKEN:-}" ]]; then
  fail "GH_TOKEN is required to create or update a GitHub release."
fi

release_tag="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
if [[ -z "$release_tag" ]]; then
  fail "RELEASE_TAG or GITHUB_REF_NAME is required."
fi

version="${release_tag#v}"
target_commit="$(git rev-list -n 1 "$release_tag" 2>/dev/null)" ||
  fail "Tag $release_tag is not present in the checkout."
notes_file="$(mktemp)"
trap 'rm -f "$notes_file"' EXIT

awk -v version="$version" '
  $0 == "## [" version "]" || index($0, "## [" version "] - ") == 1 {
    emit = 1
    next
  }
  emit && /^## \[/ {
    exit
  }
  emit {
    print
  }
' CHANGELOG.md > "$notes_file"

if [[ ! -s "$notes_file" ]]; then
  echo "::error::CHANGELOG.md has no release notes for $version."
  exit 1
fi

title="${release_tag}${RELEASE_SUFFIX:-}"
release_flags=(--draft --title "$title" --notes-file "$notes_file" --target "$target_commit")
if [[ "${RELEASE_PRE:-false}" == "true" ]]; then
  release_flags+=(--prerelease)
fi

if gh release view "$release_tag" >/dev/null 2>&1; then
  gh release edit "$release_tag" "${release_flags[@]}"
else
  gh release create "$release_tag" "${release_flags[@]}" --verify-tag
fi
