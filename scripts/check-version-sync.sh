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
# Usage: check-version-sync.sh [path-to-repo-root]
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

pom_version="$(mvn -q -f "$root_pom" help:evaluate -Dexpression=project.version -DforceStdout)"

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
# isn't numeric.
changelog_version="$(grep -m1 -E '^## \[[0-9]+\.[0-9]+\.[0-9]+\]' "$changelog" | sed -E 's/^## \[([0-9]+\.[0-9]+\.[0-9]+)\].*/\1/')"

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

printf 'check-version-sync: OK — %s == %s, both modules in sync\n' "$pom_version" "$changelog_version"
