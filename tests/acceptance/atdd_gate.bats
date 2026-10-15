#!/usr/bin/env bats
# Acceptance tests for scripts/check-revert-evidence.sh, the CI gate that requires every
# added [AC-N] acceptance test to carry a row in tests/acceptance/revert-ledger.tsv.
#
# [AC-1] fails on a diff that adds an [AC-N] test with no ledger row
# [AC-2] passes when every added [AC-N] has its ledger row
# [AC-3] skips when the diff adds no [AC-N] test

setup() {
  ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
  SCRIPT="$ROOT/scripts/check-revert-evidence.sh"
  WORK="$(mktemp -d)"
  JAVA_DIR="vietnamese-tokenizer-core/src/test/java/io/github/tylern91/vntokenizer/acceptance"

  git -C "$WORK" init -q -b main
  git -C "$WORK" config user.email "test@example.com"
  git -C "$WORK" config user.name "Test"
  git -C "$WORK" config commit.gpgsign false
  mkdir -p "$WORK/tests/acceptance" "$WORK/$JAVA_DIR"
  printf 'suite\tac\tverified_sha\tdate\n' > "$WORK/tests/acceptance/revert-ledger.tsv"
  git -C "$WORK" add -A
  git -C "$WORK" commit -q -m base
  git -C "$WORK" checkout -q -b feature
}

teardown() {
  rm -rf "$WORK"
}

commit_all() {
  git -C "$WORK" add -A
  git -C "$WORK" commit -q -m "$1"
}

# write_bats_case <file> <test-name>
# The keyword is passed as a printf argument: bats' preprocessor rewrites any line that starts
# with it, including inside a heredoc.
write_bats_case() {
  printf '%s "%s" {\n  true\n}\n' '@test' "$2" > "$1"
}

add_acceptance_tests() {
  write_bats_case "$WORK/tests/acceptance/demo.bats" "[AC-1] demo behaves"
  cat > "$WORK/$JAVA_DIR/DemoAcceptanceTest.java" <<'JAVA'
class DemoAcceptanceTest {
  @DisplayName("[AC-1] demo behaves")
  void ac1() {}
}
JAVA
}

run_gate() {
  cd "$WORK" || return 1
  run bash "$SCRIPT" main tests/acceptance/revert-ledger.tsv
}

@test "[AC-1] fails on a diff that adds an [AC-N] test with no ledger row" {
  add_acceptance_tests
  commit_all "add acs"
  run_gate
  [ "$status" -eq 1 ]
  [[ "$output" == *"::error::"* ]]
  [[ "$output" == *"demo.bats::AC-1"* ]]
  [[ "$output" == *"DemoAcceptanceTest.java::AC-1"* ]]
}

@test "[AC-2] passes when the ledger row exists" {
  add_acceptance_tests
  printf 'demo.bats\tAC-1\tabc1234\t2026-01-01\nDemoAcceptanceTest.java\tAC-1\tabc1234\t2026-01-01\n' \
    >> "$WORK/tests/acceptance/revert-ledger.tsv"
  commit_all "add acs with evidence"
  run_gate
  [ "$status" -eq 0 ]
  [[ "$output" == *"PASS:ac-gate:revert-evidence:2/2"* ]]
}

@test "[AC-3] skips when no [AC-N] test was added" {
  write_bats_case "$WORK/tests/acceptance/plain.bats" "plain behaviour"
  # An [AC-N] outside the acceptance directories is not subject to the gate.
  write_bats_case "$WORK/tests/other.bats" "[AC-9] not an acceptance suite"
  commit_all "no acs"
  run_gate
  [ "$status" -eq 0 ]
  [[ "$output" == *"SKIP:ac-gate:revert-evidence:no-new-or-changed-acs"* ]]
}
