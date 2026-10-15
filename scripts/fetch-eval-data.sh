#!/usr/bin/env bash
# fetch-eval-data.sh — Download the UD_Vietnamese-VTB treebank splits used by the segmentation
# eval harness (SegmentationEval) into eval-data/ and verify a pinned sha256 for each file.
#
# The data is fetched at a PINNED commit of the upstream repo (not master), and every file is
# checked against a hardcoded sha256, so a moved branch or a tampered mirror fails loudly instead
# of silently shifting the eval baseline. eval-data/ is gitignored and never packaged; the
# CC BY-SA 4.0 attribution lives in docs/eval-data-NOTICE.md.
#
# To bump the pin: change UD_COMMIT, re-download, recompute the three sha256 values below, and
# re-measure eval-baseline.json in the same PR.
#
# Usage: fetch-eval-data.sh [dest-dir]            (default: <repo-root>/eval-data)
# Env:   EVAL_DATA_BASE_URL  override the download base (tests point it at a file:// fake)
set -Eeuo pipefail

UD_REPO="UniversalDependencies/UD_Vietnamese-VTB"
UD_COMMIT="3c971fd1c5d03561e00b267d3d0057b81a9bea84"

SPLITS=(train dev test)

# case-based lookup (not `declare -A`) so the script also runs on macOS's bash 3.2.
expected_sha256() {
  case "$1" in
    train) echo "1df180722db99b724f7368aca50597133bee28606de3394cd8d12a57da89978d" ;;
    dev) echo "0b779257b9225575b3b30e15d64cc4ef9945a77f8cceb9f92d794b483a2752dd" ;;
    test) echo "abc1b0ea98661bbb494a8c0da123ba2fe1a3ef71322c380fe66468884d9b4285" ;;
    *) return 1 ;;
  esac
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="${1:-${repo_root}/eval-data}"
base_url="${EVAL_DATA_BASE_URL:-https://raw.githubusercontent.com/${UD_REPO}/${UD_COMMIT}}"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

mkdir -p "$dest"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

for split in "${SPLITS[@]}"; do
  name="vi_vtb-ud-${split}.conllu"
  want="$(expected_sha256 "$split")"
  target="${dest}/${name}"

  if [[ -f "$target" && "$(sha256_of "$target")" == "$want" ]]; then
    printf 'fetch-eval-data: %s already present and verified\n' "$name"
    continue
  fi

  curl --fail --silent --show-error --location --retry 3 \
    --output "${tmp}/${name}" "${base_url}/${name}"

  got="$(sha256_of "${tmp}/${name}")"
  if [[ "$got" != "$want" ]]; then
    printf 'fetch-eval-data: CHECKSUM MISMATCH for %s\n  expected %s\n  got      %s\n' \
      "$name" "$want" "$got" >&2
    exit 1
  fi

  mv "${tmp}/${name}" "$target"
  printf 'fetch-eval-data: fetched %s (sha256 verified)\n' "$name"
done

printf 'fetch-eval-data: OK — %s splits in %s\n' "${#SPLITS[@]}" "$dest"
