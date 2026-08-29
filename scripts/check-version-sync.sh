#!/usr/bin/env bash
# check-version-sync.sh — Assert the Maven reactor version matches the CHANGELOG's
# most recently released heading, both module poms agree with the root, and the
# version is not a -SNAPSHOT.
#
# Why: `mvn versions:set` bumps the root <version> and both modules'
# <parent><version> together, but nothing enforces that CHANGELOG.md's finalized
# release heading (`## [Unreleased]` -> `## [X.Y.Z] - DATE`) moves in lockstep, or
# that a partial/hand-edited bump didn't leave one module's <parent><version> behind.
#
# A 4th, optional assertion validates the pom bump against a PR's semver label -
# omit <bump-label> entirely to skip it (there is a real difference between "no
# argument" and "" — see below):
#
#   <bump-label> is major/minor/patch  -> pom_version must equal bump(latest_tag, label)
#   <bump-label> is "" (skip-release/no label) -> pom_version must equal latest_tag —
#     a non-releasing PR must not move the version and silently arm the next release
#   no tags exist in the repo at all   -> skipped regardless of <bump-label> — any
#     version is valid for a first release, and bump-version.sh hard-fails on an
#     empty <latest_tag> anyway
#
# Usage: check-version-sync.sh [path-to-repo-root] [bump-label]
set -Eeuo pipefail

root="${1:-.}"
root_pom="${root}/pom.xml"
changelog="${root}/CHANGELOG.md"

for f in "$root_pom" "$changelog"; do
  if [[ ! -f "$f" ]]; then
    printf 'check-version-sync: missing file %s\n' "$f" >&2
    exit 1
  fi
done

pom_version="$(bash "$(dirname "${BASH_SOURCE[0]}")/pom-version.sh" "$root")"

if [[ -z "$pom_version" ]]; then
  printf 'check-version-sync: could not resolve project.version from %s\n' "$root_pom" >&2
  exit 1
fi

if [[ "$pom_version" == *-SNAPSHOT ]]; then
  printf 'check-version-sync: %s has a -SNAPSHOT version (%s) — finalize before merging to main\n' \
    "$root_pom" "$pom_version" >&2
  exit 1
fi

# Both modules must inherit the same <parent><version> as the root — versions:set
# moves them together, but a hand edit or partial bump can leave one behind.
for module in vietnamese-tokenizer-core vietnamese-tokenizer-dicts; do
  module_pom="${root}/${module}/pom.xml"
  if [[ ! -f "$module_pom" ]]; then
    printf 'check-version-sync: missing file %s\n' "$module_pom" >&2
    exit 1
  fi

  parent_version="$(awk '
    /<parent>/ { p = 1 }
    /<\/parent>/ { p = 0 }
    p && /<version>/ {
      gsub(/<\/?version>/, "")
      gsub(/^[ \t]+|[ \t]+$/, "")
      print
      exit
    }
  ' "$module_pom")"

  if [[ "$parent_version" != "$pom_version" ]]; then
    printf 'check-version-sync: MISMATCH — %s <parent><version> is "%s" but %s is "%s"\n' \
      "$module_pom" "$parent_version" "$root_pom" "$pom_version" >&2
    exit 1
  fi
done

# First "## [X.Y.Z]" heading — skips "## [Unreleased]" since its bracket content
# isn't numeric. `|| true` matters: under pipefail, grep matching nothing makes the
# pipeline exit 1 even though sed still succeeds, which would abort the script here
# instead of reaching the "no released heading found" check below.
changelog_version="$(grep -m1 -E '^## \[[0-9]+\.[0-9]+\.[0-9]+\]' "$changelog" | sed -E 's/^## \[([0-9]+\.[0-9]+\.[0-9]+)\].*/\1/' || true)"

if [[ -z "$changelog_version" ]]; then
  printf 'check-version-sync: no released "## [X.Y.Z]" heading found in %s\n' "$changelog" >&2
  exit 1
fi

if [[ "$pom_version" != "$changelog_version" ]]; then
  printf 'check-version-sync: MISMATCH — %s version is "%s" but CHANGELOG.md top release is "%s"\n' \
    "$root_pom" "$pom_version" "$changelog_version" >&2
  printf 'Bump the pom.xml version to match, or finalize CHANGELOG.md if it is stale.\n' >&2
  exit 1
fi

# Bump-label assertion — see the usage comment at the top of this file for the
# three-row matrix. `${2+is_set}` (not `${2:-}`) is deliberate: it distinguishes
# "no 2nd argument at all" (skip this check) from "2nd argument passed as an empty
# string" (row 2 — validate that a non-releasing PR left the version alone).
if [[ "${2+is_set}" == "is_set" ]]; then
  label="$2"
  latest_tag="$(git -C "$root" tag --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n1 || true)"

  if [[ -n "$latest_tag" ]]; then
    if [[ -n "$label" ]]; then
      expected="$(bash "$(dirname "${BASH_SOURCE[0]}")/bump-version.sh" "$latest_tag" "$label")"
      if [[ "v${pom_version}" != "$expected" ]]; then
        printf 'check-version-sync: MISMATCH — pom.xml is v%s but label "%s" off %s implies %s\n' \
          "$pom_version" "$label" "$latest_tag" "$expected" >&2
        exit 1
      fi
    elif [[ "v${pom_version}" != "$latest_tag" ]]; then
      printf 'check-version-sync: MISMATCH — pom.xml is v%s but this PR has no release label; a non-releasing PR must not move the version past %s\n' \
        "$pom_version" "$latest_tag" >&2
      exit 1
    fi
  fi
fi

printf 'check-version-sync: OK — %s == %s, both modules in sync\n' "$pom_version" "$changelog_version"
