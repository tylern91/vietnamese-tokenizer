#!/usr/bin/env bats
# Coverage for scripts/check-version-sync.sh — reactor-wide version consistency
# and the pull_request bump-label assertion matrix (see the script's own header
# comment for the three-row matrix this exercises).

setup() {
  ROOT="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
  SCRIPT="$ROOT/scripts/check-version-sync.sh"
  WORK="$(mktemp -d)"
}

teardown() {
  rm -rf "$WORK"
}

# make_fixture <version> <changelog_version> [module_version_override]
# Builds a minimal reactor (root pom + 2 module poms + CHANGELOG.md) under $WORK,
# git-initialized so the bump-label assertion's `git tag` lookups work. Identity
# is set per-repo since CI runners carry no global git user config.
make_fixture() {
  local version="$1"
  local changelog_version="$2"
  local module_version="${3:-$version}"
  mkdir -p "$WORK/vietnamese-tokenizer-core" "$WORK/vietnamese-tokenizer-dicts"

  cat > "$WORK/pom.xml" <<EOF
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.tylern91</groupId>
  <artifactId>vietnamese-tokenizer</artifactId>
  <version>${version}</version>
  <packaging>pom</packaging>
</project>
EOF

  for m in vietnamese-tokenizer-core vietnamese-tokenizer-dicts; do
    cat > "$WORK/$m/pom.xml" <<EOF
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.tylern91</groupId>
    <artifactId>vietnamese-tokenizer</artifactId>
    <version>${module_version}</version>
  </parent>
  <artifactId>${m}</artifactId>
</project>
EOF
  done

  cat > "$WORK/CHANGELOG.md" <<EOF
# Changelog

## [${changelog_version}] - 2026-01-01

- stuff
EOF

  git -C "$WORK" init -q
  git -C "$WORK" config user.email "test@example.com"
  git -C "$WORK" config user.name "Test"
  git -C "$WORK" add -A
  git -C "$WORK" commit -q -m init
}

tag_fixture() {
  git -C "$WORK" tag -m x "$1"
}

@test "passes when pom, modules, and CHANGELOG all agree (no label arg)" {
  make_fixture "1.0.0" "1.0.0"
  run "$SCRIPT" "$WORK"
  [ "$status" -eq 0 ]
}

@test "fails on a -SNAPSHOT version" {
  make_fixture "1.0.0-SNAPSHOT" "1.0.0-SNAPSHOT"
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"SNAPSHOT"* ]]
}

@test "fails when a module's <parent><version> drifts from root" {
  make_fixture "1.0.0" "1.0.0" "0.9.0"
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"MISMATCH"* ]]
}

@test "fails when CHANGELOG's top heading disagrees with pom" {
  make_fixture "1.0.1" "1.0.0"
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"MISMATCH"* ]]
}

@test "fails when CHANGELOG has no released heading" {
  make_fixture "1.0.0" "1.0.0"
  cat > "$WORK/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

- stuff
EOF
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"no released"* ]]
}

@test "no tags in repo: bump-label assertion is skipped regardless of label" {
  make_fixture "0.1.0" "0.1.0"
  run "$SCRIPT" "$WORK" "minor"
  [ "$status" -eq 0 ]
}

@test "no tags in repo: empty label is also skipped" {
  make_fixture "0.1.0" "0.1.0"
  run "$SCRIPT" "$WORK" ""
  [ "$status" -eq 0 ]
}

@test "label matches the bump implied by the latest tag" {
  make_fixture "1.1.0" "1.1.0"
  tag_fixture v1.0.0
  run "$SCRIPT" "$WORK" "minor"
  [ "$status" -eq 0 ]
}

@test "label disagrees with the actual pom bump" {
  make_fixture "1.1.0" "1.1.0"
  tag_fixture v1.0.0
  run "$SCRIPT" "$WORK" "patch"
  [ "$status" -ne 0 ]
  [[ "$output" == *"MISMATCH"* ]]
}

@test "empty label requires the pom to stay at the latest tag" {
  make_fixture "1.0.0" "1.0.0"
  tag_fixture v1.0.0
  run "$SCRIPT" "$WORK" ""
  [ "$status" -eq 0 ]
}

@test "empty label rejects a pom that moved past the latest tag" {
  make_fixture "1.1.0" "1.1.0"
  tag_fixture v1.0.0
  run "$SCRIPT" "$WORK" ""
  [ "$status" -ne 0 ]
  [[ "$output" == *"no release label"* ]]
}

@test "omitting the label argument skips the bump assertion even with tags present" {
  make_fixture "1.1.0" "1.1.0"
  tag_fixture v1.0.0
  run "$SCRIPT" "$WORK"
  [ "$status" -eq 0 ]
}
