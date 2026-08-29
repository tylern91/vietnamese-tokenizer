#!/usr/bin/env bash
# pom-version.sh — print the root reactor's <version> to stdout without
# shelling out to Maven. Used where a JVM start is wasted cost (e.g. the
# release job reads it before setup-java runs).
#
# Safe only because the root pom has no <parent> element: with no <parent>,
# the first <version> in document order is unambiguously the project's own
# version. Fails closed — rather than silently printing the wrong number —
# if a <parent> is ever introduced above it, since <parent><version> would
# then be the first match instead of the project's own.
#
# Usage: pom-version.sh [path-to-repo-root]
set -Eeuo pipefail

root="${1:-.}"
root_pom="${root}/pom.xml"

if [[ ! -f "$root_pom" ]]; then
  printf 'pom-version: missing file %s\n' "$root_pom" >&2
  exit 1
fi

version="$(awk -v pom="$root_pom" '
  /<parent>/ {
    printf "pom-version: <parent> found before <version> in %s - the first <version> in document order is no longer guaranteed to be the project version. Update pom-version.sh to resolve the correct element instead of assuming document order.\n", pom > "/dev/stderr"
    exit 2
  }
  /<version>/ {
    line = $0
    sub(/^[^<]*<version>/, "", line)
    sub(/<\/version>.*$/, "", line)
    gsub(/^[ \t]+|[ \t]+$/, "", line)
    print line
    exit
  }
' "$root_pom")"

if [[ -z "$version" ]]; then
  printf 'pom-version: could not find a <version> element in %s\n' "$root_pom" >&2
  exit 1
fi

printf '%s\n' "$version"
