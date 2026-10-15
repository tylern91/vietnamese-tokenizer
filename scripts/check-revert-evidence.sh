#!/usr/bin/env bash
# check-revert-evidence.sh — Require a revert-ledger row for every acceptance test a branch adds.
#
# An acceptance test only counts once someone watched it fail against the pre-change code and
# pass against the post-change code. The ledger records that observation; this gate stops a PR
# from adding an [AC-N] test without it.
#
# Scans `git diff <base>...HEAD` for ADDED lines of:
#   @DisplayName("[AC-N] ...   in  */acceptance/*AcceptanceTest.java
#   @test "[AC-N] ...          in  tests/acceptance/*.bats
# Each (suite = file basename, AC-N) pair needs a row in the ledger
# (TSV: suite, ac, verified_sha, date; first row is a header).
#
# Output: PASS:ac-gate:revert-evidence:N/N, SKIP:ac-gate:revert-evidence:no-new-or-changed-acs
# (exit 0), or ::error:: lines (exit 1). Bad invocation or an unknown base ref exits 2.
#
# Usage: check-revert-evidence.sh [base-ref] [ledger-path]
set -Eeuo pipefail

base="${1:-origin/main}"
ledger="${2:-tests/acceptance/revert-ledger.tsv}"

if ! git rev-parse --verify --quiet "${base}^{commit}" >/dev/null; then
  printf '::error::check-revert-evidence: base ref "%s" not found (fetch it first)\n' "$base" >&2
  exit 2
fi

# Renames are disabled so a renamed suite shows all its AC lines as added: the suite key is the
# basename, so a rename needs its own ledger rows.
added_acs="$(git -c core.quotepath=off diff --no-color --no-ext-diff --no-renames --unified=0 \
    "${base}...HEAD" -- '*/acceptance/*AcceptanceTest.java' 'tests/acceptance/*.bats' |
  awk '
    /^\+\+\+ / {
      file = $0
      sub(/^\+\+\+ (b\/)?/, "", file)
      n = split(file, parts, "/")
      suite = (file == "/dev/null") ? "" : parts[n]
      next
    }
    suite == "" || substr($0, 1, 1) != "+" { next }
    suite ~ /\.java$/ && $0 ~ /^\+[[:space:]]*@DisplayName\("\[AC-[0-9]+\]/ { emit($0) }
    suite ~ /\.bats$/ && $0 ~ /^\+[[:space:]]*@test "\[AC-[0-9]+\]/ { emit($0) }
    function emit(line) {
      match(line, /\[AC-[0-9]+\]/)
      print suite "::" substr(line, RSTART + 1, RLENGTH - 2)
    }
  ' | sort -u)"

if [[ -z "$added_acs" ]]; then
  echo "SKIP:ac-gate:revert-evidence:no-new-or-changed-acs"
  exit 0
fi

total=0
missing=()
while IFS= read -r key; do
  [[ -z "$key" ]] && continue
  total=$((total + 1))
  suite="${key%%::*}"
  ac="${key#*::}"
  if [[ ! -f "$ledger" ]] ||
    ! awk -F'\t' -v s="$suite" -v a="$ac" '$1 == s && $2 == a { found = 1 } END { exit !found }' "$ledger"; then
    missing+=("$key")
  fi
done <<<"$added_acs"

if ((${#missing[@]} > 0)); then
  printf '::error::new [AC-N] test(s) with no %s row (revert-verify each one and record it):\n' "$ledger"
  printf '::error::  %s\n' "${missing[@]}"
  exit 1
fi

echo "PASS:ac-gate:revert-evidence:${total}/${total}"
