#!/usr/bin/env bats
# Coverage for scripts/fetch-eval-data.sh — checksum-mismatch behavior only (no network: the
# download base is pointed at a local file:// fake).

setup() {
  ROOT="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
  SCRIPT="$ROOT/scripts/fetch-eval-data.sh"
  WORK="$(mktemp -d)"
  mkdir -p "$WORK/fake"
  for split in train dev test; do
    printf 'not the real treebank\n' > "$WORK/fake/vi_vtb-ud-$split.conllu"
  done
}

teardown() {
  rm -rf "$WORK"
}

@test "fails loudly on a checksum mismatch and names the file" {
  run env EVAL_DATA_BASE_URL="file://$WORK/fake" "$SCRIPT" "$WORK/out"
  [ "$status" -eq 1 ]
  [[ "$output" == *"CHECKSUM MISMATCH"* ]]
  [[ "$output" == *"vi_vtb-ud-train.conllu"* ]]
}

@test "does not leave an unverified file in the destination" {
  run env EVAL_DATA_BASE_URL="file://$WORK/fake" "$SCRIPT" "$WORK/out"
  [ "$status" -eq 1 ]
  [ ! -e "$WORK/out/vi_vtb-ud-train.conllu" ]
}

@test "replaces a stale local file only after the download verifies" {
  mkdir -p "$WORK/out"
  printf 'stale\n' > "$WORK/out/vi_vtb-ud-train.conllu"
  run env EVAL_DATA_BASE_URL="file://$WORK/fake" "$SCRIPT" "$WORK/out"
  [ "$status" -eq 1 ]
  [ "$(cat "$WORK/out/vi_vtb-ud-train.conllu")" = "stale" ]
}
