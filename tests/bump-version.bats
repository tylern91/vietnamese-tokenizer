#!/usr/bin/env bats
# Coverage for scripts/bump-version.sh — pure function, no fixtures needed.

setup() {
  ROOT="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
  SCRIPT="$ROOT/scripts/bump-version.sh"
}

@test "patch bump increments the last component" {
  run "$SCRIPT" v0.2.1 patch
  [ "$status" -eq 0 ]
  [ "$output" = "v0.2.2" ]
}

@test "minor bump increments minor and resets patch" {
  run "$SCRIPT" v1.2.3 minor
  [ "$status" -eq 0 ]
  [ "$output" = "v1.3.0" ]
}

@test "major bump increments major and resets minor+patch" {
  run "$SCRIPT" v1.2.3 major
  [ "$status" -eq 0 ]
  [ "$output" = "v2.0.0" ]
}

@test "minor bump on a zero patch stays zero" {
  run "$SCRIPT" v2.0.0 minor
  [ "$status" -eq 0 ]
  [ "$output" = "v2.1.0" ]
}

@test "rejects a malformed tag" {
  run "$SCRIPT" 2.0.0 patch
  [ "$status" -ne 0 ]
  [[ "$output" == *"invalid tag format"* ]]
}

@test "rejects an unknown label" {
  run "$SCRIPT" v1.0.0 rc
  [ "$status" -ne 0 ]
  [[ "$output" == *"invalid label"* ]]
}

@test "rejects an empty tag argument" {
  run "$SCRIPT" "" patch
  [ "$status" -ne 0 ]
}

@test "fails when no arguments are given" {
  run "$SCRIPT"
  [ "$status" -ne 0 ]
}

@test "fails when the label argument is missing" {
  run "$SCRIPT" v1.0.0
  [ "$status" -ne 0 ]
}
