#!/usr/bin/env bash
# Fast-forward `main` to a freshly-published release tag.
#
# Why this exists: main is the JitPack main-SNAPSHOT surface and the branch
# main-tracking consumers follow, but releases are cut from dev and tagged
# there. Without this, promotion is a manual step that gets forgotten — main
# silently rotted 2 releases / 255 commits behind before the 0.14.1 catch-up.
#
# Capability-join behaviour (active by default, no setup required):
#   * If GH_TOKEN can bypass main's protection (a GitHub Ruleset bypass actor),
#     the fast-forward push succeeds and main is promoted silently.
#   * Otherwise the push is rejected and this script FAILS LOUDLY, turning the
#     release red so a forgotten promotion cannot pass unnoticed — the exact
#     failure mode that caused the rot. The maintainer then promotes manually.
# Provisioning the MAIN_PROMOTE_TOKEN secret upgrades the loud alert to silent
# automation; it is never required for the guarantee to hold.
#
# This NEVER weakens protection. The push fast-forwards the already-signed,
# already-CI-verified tag commit, so required-signatures + linear-history hold
# automatically. Auto-toggling enforce_admins was rejected: an unattended job
# that disables protection can leave main silently unprotected on failure.
#
# Testability seam: PROMOTE_REMOTE_URL overrides the push/verify remote (a local
# bare repo in promote-main.test.sh); when unset, the GitHub token URL is built
# from GH_TOKEN + GITHUB_REPOSITORY. `origin` (set by actions/checkout) is the
# fetch source in both cases.
set -euo pipefail

fail() {
  echo "::error::$*"
  exit 1
}

TAG="${TAG:?TAG (released tag name, e.g. v0.14.1) is required}"

remote_url="${PROMOTE_REMOTE_URL:-}"
if [[ -z "${remote_url}" ]]; then
  : "${GH_TOKEN:?GH_TOKEN is required when PROMOTE_REMOTE_URL is unset}"
  : "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required when PROMOTE_REMOTE_URL is unset}"
  remote_url="https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
fi

tag_sha="$(git rev-list -n 1 "${TAG}" 2>/dev/null)" ||
  fail "Tag ${TAG} is not present in the checkout (need fetch-depth: 0)"

git fetch --no-tags origin main ||
  fail "Could not fetch origin/main"
main_sha="$(git rev-parse FETCH_HEAD)"

# Idempotent: nothing to do if main already contains the tag commit.
if git merge-base --is-ancestor "${tag_sha}" "${main_sha}"; then
  echo "::notice::origin/main (${main_sha}) already contains ${TAG} (${tag_sha}); nothing to promote."
  exit 0
fi

# Fast-forward-only guard: refuse unless main is a strict ancestor of the tag
# commit. NEVER force-push — divergence is a human-investigate event.
git merge-base --is-ancestor "${main_sha}" "${tag_sha}" ||
  fail "origin/main (${main_sha}) is not an ancestor of ${TAG} (${tag_sha}); refusing non-fast-forward promotion."

if ! git push "${remote_url}" "${tag_sha}:refs/heads/main"; then
  fail "main lags ${TAG} (${tag_sha}) and could not be auto-promoted — is GH_TOKEN a main branch-protection bypass actor? Publish already succeeded; promote main manually (relax enforce_admins, ff-push ${tag_sha}:main, re-enable) or set the MAIN_PROMOTE_TOKEN secret."
fi

# Verify the promotion actually landed before trusting it (git over the same
# remote — no gh dependency, works for both the GitHub URL and a local bare repo).
landed_sha="$(git ls-remote "${remote_url}" refs/heads/main | cut -f1)"
[[ "${landed_sha}" == "${tag_sha}" ]] ||
  fail "Post-push main is ${landed_sha:-<empty>}, expected ${tag_sha}"

echo "::notice::Promoted main to ${TAG} (${tag_sha})."
