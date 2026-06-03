#!/usr/bin/env bash
# Falsifying tests for promote-main.sh. Real git against a local bare repo
# acting as `origin` — no mocks, no fakes. Each case is built so that breaking
# the corresponding logic in promote-main.sh makes the assertion FAIL:
#   1. ff-promote   — wrong/absent push leaves main behind the tag.
#   2. idempotent   — main AHEAD of tag; without the idempotency short-circuit
#                     the fast-forward guard would wrongly reject (exit 1).
#   3. divergence   — asserts the guard's OWN "non-fast-forward" refusal, so
#                     git's generic non-ff backstop cannot mask a removed guard;
#                     and that main is never mutated on divergence.
#   4. missing tag  — absent tag fails fast.
set -euo pipefail

SUT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/promote-main.sh"
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null
passed=0 failed=0

setup() { # fresh bare `origin` with main=C1 and a working clone
  ROOT="$(mktemp -d)"; BARE="$ROOT/origin.git"; WORK="$ROOT/work"
  git init -q --bare "$BARE"
  git init -q "$WORK"; git -C "$WORK" remote add origin "$BARE"
  git -C "$WORK" commit -q --allow-empty -m C1; git -C "$WORK" branch -M main
  git -C "$WORK" push -q origin main
}
commit() { git -C "$WORK" commit -q --allow-empty -m "$1"; git -C "$WORK" rev-parse HEAD; }
run_sut() { ( cd "$WORK" && PROMOTE_REMOTE_URL="$BARE" TAG="$1" bash "$SUT" ) 2>&1; }
remote_main() { git ls-remote "$BARE" refs/heads/main | cut -f1; }
check() {
  if [[ "$2" == 1 ]]; then echo "PASS: $1"; passed=$((passed+1));
  else echo "FAIL: $1"; failed=$((failed+1)); fi
}

# 1. fast-forward promote: bare main=C1, tag at C3 → main advances to C3.
setup; commit C2 >/dev/null; c3="$(commit C3)"; git -C "$WORK" tag v1 "$c3"
if run_sut v1 >/dev/null 2>&1; then rc=0; else rc=$?; fi
ok=0; if [[ $rc -eq 0 && "$(remote_main)" == "$c3" ]]; then ok=1; fi
check "ff-promote advances main to the tag" "$ok"

# 2. idempotent (main AHEAD of tag): bare main=C3, tag=C2 → no-op, main unchanged.
setup; commit C2 >/dev/null; c3="$(commit C3)"; git -C "$WORK" push -q origin main
git -C "$WORK" tag v1 "$(git -C "$WORK" rev-parse HEAD~1)"
if run_sut v1 >/dev/null 2>&1; then rc=0; else rc=$?; fi
ok=0; if [[ $rc -eq 0 && "$(remote_main)" == "$c3" ]]; then ok=1; fi
check "idempotent no-op when main already contains the tag" "$ok"

# 3. divergence: tag=A, bare main=B (siblings off C1) → guard refuses, main intact.
setup; a="$(commit A)"; git -C "$WORK" tag v1 "$a"
git -C "$WORK" reset -q --hard HEAD~1; commit B >/dev/null; git -C "$WORK" push -q origin main
b="$(remote_main)"; out="$(run_sut v1 || true)"
# Assert the guard's OWN phrase ("refusing …") — git's native non-ff rejection
# also prints "non-fast-forward", so a looser match would not falsify guard loss.
ok=0; if [[ "$(remote_main)" == "$b" ]] && grep -q 'refusing non-fast-forward promotion' <<<"$out"; then ok=1; fi
check "divergence refuses with the guard's own message, main unchanged" "$ok"

# 4. missing tag fails fast.
setup; out="$(run_sut v-absent || true)"
ok=0; if grep -q 'not present in the checkout' <<<"$out"; then ok=1; fi
check "missing tag fails fast" "$ok"

echo "--- promote-main.test.sh: ${passed} passed, ${failed} failed ---"
[[ $failed -eq 0 ]]
