#!/usr/bin/env bats
# Coverage for scripts/pom-version.sh — Maven-free root <version> extraction.

setup() {
  ROOT="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
  SCRIPT="$ROOT/scripts/pom-version.sh"
  WORK="$(mktemp -d)"
}

teardown() {
  rm -rf "$WORK"
}

write_pom() {
  cat > "$WORK/pom.xml"
}

@test "prints the real repo's root version" {
  run "$SCRIPT" "$ROOT"
  [ "$status" -eq 0 ]
  [[ "$output" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

@test "extracts <version> with no <parent>" {
  write_pom <<'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.tylern91</groupId>
  <artifactId>vietnamese-tokenizer</artifactId>
  <version>1.2.3</version>
</project>
EOF
  run "$SCRIPT" "$WORK"
  [ "$status" -eq 0 ]
  [ "$output" = "1.2.3" ]
}

@test "extracts <version> regardless of surrounding indentation" {
  write_pom <<'EOF'
<project>
      <version>9.9.9</version>
</project>
EOF
  run "$SCRIPT" "$WORK"
  [ "$status" -eq 0 ]
  [ "$output" = "9.9.9" ]
}

@test "fails closed when <parent> precedes <version>" {
  write_pom <<'EOF'
<project>
  <parent>
    <groupId>io.github.tylern91</groupId>
    <artifactId>vietnamese-tokenizer</artifactId>
    <version>1.2.3</version>
  </parent>
  <artifactId>vietnamese-tokenizer-core</artifactId>
</project>
EOF
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"<parent> found before <version>"* ]]
}

@test "fails when pom.xml is missing" {
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"missing file"* ]]
}

@test "fails when pom.xml has no <version> element at all" {
  write_pom <<'EOF'
<project>
  <artifactId>vietnamese-tokenizer</artifactId>
</project>
EOF
  run "$SCRIPT" "$WORK"
  [ "$status" -ne 0 ]
  [[ "$output" == *"could not find a <version> element"* ]]
}
