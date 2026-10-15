#!/usr/bin/env bats
# Acceptance suite for the CI/supply-chain hardening of the GitHub Actions
# workflows. Plain grep/awk only: these run on a bare runner with just bats.

setup() {
  ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
  WORKFLOWS="$ROOT/.github/workflows"
  RELEASE="$WORKFLOWS/release.yml"
}

# Prints "<file>: <job>" for every job in $1 whose body lacks a job-level
# timeout-minutes (job keys sit at 2-space indent, their fields at 4).
jobs_without_timeout() {
  awk -v file="$(basename "$1")" '
    function flush() { if (job != "" && !seen) print file ": " job }
    /^jobs:/ { in_jobs = 1; next }
    in_jobs && /^[^ #]/ { flush(); job = ""; in_jobs = 0 }
    in_jobs && /^  [A-Za-z0-9_-]+:[ ]*$/ { flush(); job = $1; sub(/:$/, "", job); seen = 0; next }
    in_jobs && /^    timeout-minutes:[ ]*[0-9]+/ { seen = 1 }
    END { flush() }
  ' "$1"
}

# Prints the name of every step in $1 whose run line invokes a -Prelease
# maven build but whose own env: does not set MAVEN_GPG_PASSPHRASE.
release_steps_without_env_passphrase() {
  awk '
    function flush() { if (step != "" && release && !hasenv) print step }
    /^      - / { flush(); step = ""; release = 0; hasenv = 0 }
    /^      - name:/ { step = $0; sub(/^      - name:[ ]*/, "", step) }
    /mvn .*-Prelease/ { release = 1 }
    /^          MAVEN_GPG_PASSPHRASE: \$\{\{ secrets\.GPG_PASSPHRASE \}\}/ { hasenv = 1 }
    END { flush() }
  ' "$1"
}

@test "[AC-1] every runs-on in every workflow is exactly ubuntu-26.04-arm" {
  run grep -hE '^[[:space:]]*runs-on:' "$WORKFLOWS"/*.yml
  [ "$status" -eq 0 ]
  [ -n "$output" ]
  bad="$(printf '%s\n' "$output" | grep -vE '^[[:space:]]*runs-on:[[:space:]]*ubuntu-26\.04-arm[[:space:]]*$' || true)"
  [ -z "$bad" ] || { printf 'non-ARM runner(s):\n%s\n' "$bad"; false; }
}

@test "[AC-2] dependabot.yml updates maven and github-actions weekly" {
  f="$ROOT/.github/dependabot.yml"
  [ -f "$f" ]
  grep -qE '^[[:space:]]*-?[[:space:]]*package-ecosystem:[[:space:]]*"?maven"?[[:space:]]*$' "$f"
  grep -qE '^[[:space:]]*-?[[:space:]]*package-ecosystem:[[:space:]]*"?github-actions"?[[:space:]]*$' "$f"
  [ "$(grep -cE '^[[:space:]]*interval:[[:space:]]*"?weekly"?[[:space:]]*$' "$f")" -eq 2 ]
}

@test "[AC-3] release.yml does not grant pull-requests: write" {
  run grep -nE 'pull-requests:[[:space:]]*write' "$RELEASE"
  [ "$status" -eq 1 ]
}

@test "[AC-4] no workflow passes -Dgpg.passphrase; release maven steps get MAVEN_GPG_PASSPHRASE via env:" {
  run grep -nE -- '-Dgpg\.passphrase' "$WORKFLOWS"/*.yml
  [ "$status" -eq 1 ]
  # Both the verify (signing) and deploy steps must run with the env var set.
  [ "$(grep -cE 'mvn .*-Prelease' "$RELEASE")" -eq 2 ]
  missing="$(release_steps_without_env_passphrase "$RELEASE")"
  [ -z "$missing" ] || { printf 'step(s) missing MAVEN_GPG_PASSPHRASE env:\n%s\n' "$missing"; false; }
}

@test "[AC-5] every job in every workflow declares timeout-minutes" {
  missing=""
  for f in "$WORKFLOWS"/*.yml; do
    missing+="$(jobs_without_timeout "$f")"$'\n'
  done
  missing="$(printf '%s' "$missing" | sed '/^$/d')"
  [ -z "$missing" ] || { printf 'job(s) without timeout-minutes:\n%s\n' "$missing"; false; }
  # publish must outlive the pom's 1800s Central wait (30 min) plus build time.
  run awk '/^  publish:/{p=1} p && /^    timeout-minutes:/{print $2; exit}' "$RELEASE"
  [ "$status" -eq 0 ]
  [ "$output" -gt 30 ]
}
