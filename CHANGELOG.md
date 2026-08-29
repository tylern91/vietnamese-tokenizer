# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- `pom.xml`: Central deployments now auto-publish (`autoPublish`) and the build waits for the
  artifact to go live (`waitUntil=published`), so a release no longer stalls in the Portal awaiting
  a manual click. Green CI does now imply published — but the converse doesn't hold: a run killed
  mid-wait (e.g. by the job timeout) leaves the deployment completing server-side, so **red no
  longer implies not-published**.
- `pom.xml`: named Central deployments `vietnamese-tokenizer <version>` instead of the default
  `Deployment`, which made concurrent Portal entries indistinguishable.
- `pom.xml`: pinned `<waitMaxTime>1800</waitMaxTime>` explicitly — the plugin's non-lowerable wait
  floor — so its coupling with `release.yml`'s `publish` job timeout is visible to whoever next
  edits either one.
- `.github/workflows/release.yml`: raised the `publish` job's `timeout-minutes` from 30 to 45. 30
  exactly equalled the plugin's wait floor, so the runner was guaranteed to be killed before the
  plugin's own wait could time out — and because `autoPublish=true`, the deployment then kept
  publishing server-side after the runner died, with CI reporting red for a version that shipped
  anyway.
- `.github/workflows/release.yml`: gated the `publish` job behind a new `central` GitHub
  Environment with a required reviewer, restoring the human checkpoint that `autoPublish=true`
  removes — a PR touching `release.yml`, `scripts/*`, or `pom.xml` runs with Central/GPG secrets in
  scope from the PR head, not `main`.
- `.github/workflows/release.yml`: scoped `CENTRAL_*`, `GPG_PASSPHRASE`, and `GH_TOKEN` to the
  individual steps that use them instead of job-level `env`, so unrelated steps — including
  third-party Maven plugin code — can no longer read them.
- `.github/workflows/release.yml`: dropped `cache: maven` on the `publish` job in favor of an
  isolated `-Dmaven.repo.local=$RUNNER_TEMP/m2`, so a signing job never restores a `~/.m2` store
  written by a PR-controlled `ci.yml` run on the same event.
- `.github/workflows/release.yml`: reordered the `publish` job to verify → assert manifest →
  package assets → upload assets → deploy, so a deploy failure no longer strands the release
  assets, and the manifest assertion runs before the irreversible step rather than after it.
- `.github/workflows/release.yml`: added `queue: max` to the workflow's `concurrency` block — the
  default `queue: single` silently cancels a third concurrent run instead of queuing it, and
  `publish` now holds the lock for up to 45 minutes.
- `.github/workflows/release.yml`: the release version is now read from `pom.xml` (via the new
  `scripts/pom-version.sh`) instead of derived by bumping the latest git tag — the PR's semver
  label now only *validates* that the pom diff matches the bump it claims, rather than deciding
  the version itself. Closes the deferred PR #5 finding that a failed publish followed by a re-run
  silently did nothing: `pom.xml` no longer moves between the failed attempt and the retry, so the
  idempotency guard now recognizes the existing tag and lets `publish` retry instead of computing a
  new, unreachable version.
- `.github/workflows/release.yml`: added a `workflow_dispatch` trigger (`tag` input) as a documented
  recovery path for a stuck or failed `publish` — it only retries an **existing** tag and hard-fails
  if the tag isn't found; it never derives or creates a version.
- `.github/workflows/release.yml`: `CENTRAL_PUBLISH_ENABLED != 'true'` and a missing
  `GPG_PRIVATE_KEY` now hard-fail the `release` job before the tag is created, instead of silently
  producing a green run with nothing published, or an unsigned tag. The check now runs on every
  attempt, including `workflow_dispatch` recovery re-runs — it was previously gated behind the
  idempotency guard's "tag doesn't exist yet" condition, which meant it never ran on exactly the
  paths where a mid-flight config change would otherwise reproduce the original silent-skip bug.
  Dry-run previews (`<!-- release-dry-run -->`) are exempted, since nothing gets tagged, signed, or
  published either way.
- `.github/workflows/release.yml`: `steps.ver.outputs.next` and `steps.bump.outputs.label` are now
  passed to every `run:` step via `env:` instead of interpolated directly into the script text, and
  the computed release tag is format-validated against `^v[0-9]+\.[0-9]+\.[0-9]+$` immediately after
  it's derived. Git tag names may legally contain shell metacharacters, and on the
  `workflow_dispatch` path the tag is operator-supplied — so an unvalidated, directly-interpolated
  tag was a script-injection vector into a job with `contents: write` and access to the GPG signing
  key.
- `scripts/check-version-sync.sh`: now also asserts the PR's semver label agrees with the pom
  version bump (or, for a `skip-release` PR, that the pom didn't move at all) — surfacing what used
  to be a silent post-merge no-op as a red required check on the PR itself.

### Added

- `scripts/pom-version.sh`: extracts the root `pom.xml`'s `<version>` without shelling out to
  Maven; fails closed if a `<parent>` element ever precedes `<version>`.
- `tests/*.bats`: bats coverage for `bump-version.sh`, `pom-version.sh`, and
  `check-version-sync.sh`'s bump-label assertion matrix, wired into CI as a new `shell-tests` job.

### Known limitations (tracked, not fixed in this PR)

- `GPG_PRIVATE_KEY`/`GPG_PASSPHRASE`/`GPG_KEY_ID` are repository-level secrets, not scoped to the
  `central` GitHub Environment. The `central` environment's required-reviewer gate is declared only
  on the `publish` job (Central upload) — the `release` job, which imports and uses the GPG key to
  sign tags, never declares `environment: central` and can read these secrets from any trigger,
  including `workflow_dispatch`. Any collaborator with repo write access can push a branch with a
  modified `release.yml` and dispatch it, reaching GPG import without the human review that gate is
  meant to enforce. Pre-existing since PR #5's secret architecture; this PR's addition of
  `workflow_dispatch` makes it reachable via a single `gh workflow run` rather than only in theory.
  Proper remediation (re-registering the GPG secrets at the `central` environment scope) needs the
  actual secret material and is a repo-settings change outside this PR's scope — tracked for a
  dedicated follow-up rather than bundled here.

---

## [0.2.1] - 2026-08-02

### Added

- Maven Central publishing config: `<scm>`/`<developers>` metadata, source and javadoc plugins, and a `release` Maven profile (GPG signing + `central-publishing-maven-plugin`).
- `vietnamese-tokenizer-dicts`: corrected `<licenses>` to CC BY-SA 4.0 (was inheriting the parent's Apache-2.0 declaration) and a placeholder javadoc jar, since the module has no Java sources to document.
- `.github/workflows/release.yml`: label-driven release pipeline (`major`/`minor`/`patch`/`skip-release`/`breaking-change` labels) that tags, publishes a GitHub Release, and — once `CENTRAL_PUBLISH_ENABLED` is flipped on — deploys to Maven Central and uploads release assets.
- `.github/workflows/ci.yml`: a `version-sync` check verifying the poms and `CHANGELOG.md` agree on version, run as its own required status alongside the existing build.
- `scripts/bump-version.sh`, `scripts/build-release-notes.sh`, `scripts/check-version-sync.sh`: the version-bump and release-notes tooling backing the release workflow.
- `.gitattributes`: marks the three bundled `*.gz` dictionaries and `*.jar` files as binary, so `core.autocrlf` or a future `text=auto` default can't byte-mangle them.
- Probity TDD gate tuning: widened the validator's transcript window, added project-specific facts closing a broken-build/red-test ambiguity, and added deterministic regex guards (`internal.*` packages must stay unexported; commits must not bypass GPG signing or hooks).
- README rewrite: installation coordinates, usage examples with real tokenizer output, and dictionary-data attribution.

### Fixed

- `.gitignore` no longer tracks stale tdd-guard predecessor state (`.claude/tdd-guard/`) and now ignores `.claude/audits/` (agent-generated scratch output).
- `build-release-notes.sh`: synced from the canonical dotfiles copy — release bodies now end with a commit/PR/full-changelog provenance footer, and `--from-existing` is recognized in any argument position instead of only its usual slot.
- `release.yml`: the empty-notes diagnostic now names the missing `## [<tag>]` heading instead of blaming `[Unreleased]`, which was dead code since `--from-existing` is always passed.
- `pom.xml`: pinned `maven-resources-plugin`'s version in `<pluginManagement>`, closing a Maven "malformed project" warning against the dicts module's placeholder-javadoc-jar plugin declaration.
- Added Javadoc to the public API (`Token`, `TokenizeOption`, `VnTokenizer`, `module-info.java`), closing the 17 `javadoc:jar` "no comment" / missing-tag warnings CI's build job was emitting.
- `pom.xml`: disabled `maven-javadoc-plugin`'s `detectOfflineLinks`, closing a "fake javadoc directory" warning and accompanying "Error fetching link" against the dicts module, whose javadoc generation is intentionally skipped.

## [0.2.0] - 2026-07-29

### Added

- Real dictionary-based Viterbi word segmentation, replacing the earlier tokenization stubs.
- `vietnamese-tokenizer-dicts`: bundled Vietnamese Wiktionary + UVW-2026 derived dictionary data (`words.txt.gz`, `syllables.txt.gz`, `bigrams.txt.gz`), CC BY-SA 4.0, with `NOTICE` attribution.
- `DoubleArrayTrieBuilder`: builds `WordTrie`/`SyllableTrie` double-array tries from the bundled dictionaries.
- `WordTrie.longestWordFrom` / `matchesFrom` and `SyllableTrie.containsSyllable` query primitives, case-insensitive at lookup.
- `DictLoader` / `Dictionaries`: loads dictionaries from the classpath or an arbitrary filesystem path; computes unigram log-probabilities and PMI-based bigram scores.
- `ViterbiSegmenter`: forward-DP word segmentation with a two-tier out-of-vocabulary fallback (syllable-run, then single-codepoint).
- `VnTokenizer.tokenize()` / `tokenizeToStrings()`: real end-to-end tokenization, including OOV classification (`NUMBER`/`PUNCT`/`SPACE`/`WORD`) and `HOST`/`URL` mode handling.

### Fixed

- Trie matching was case-sensitive against an all-lowercase dictionary, causing capitalized Vietnamese proper nouns (e.g. "Hà Nội") to silently fail dictionary lookup and fall back to OOV. Fixed by folding to lowercase at lookup time only; output token text still preserves original casing.
- `HOST_PATTERN`'s unbounded `(?:...)+` repetition caused a `StackOverflowError` on input with thousands of dot-separated labels (`TokenizeOption.HOST`), since Java's regex engine implements repetition via recursive backtracking. Bounded the repetition counts to RFC 1035 hostname structural limits (`{1,63}` per label, `{1,127}` labels, `{2,63}` for the TLD).
- `ViterbiSegmenter`'s OOV fallback re-scanned to the next whitespace character from every position in a whitespace-free run, an O(n²) blowup on long runs of unrecognized text. Fixed by precomputing a `nextWhitespace[]` lookup array in one O(n) pass.
- `VnTokenizer.segmentWithAtoms` (`HOST`/`URL` modes) indexed the codepoint array using the regex `Matcher`'s raw `start()`/`end()` offsets, which are UTF-16 char offsets, not codepoint offsets. Any supplementary-plane codepoint (e.g. an emoji) preceding a matched URL/host desynced the two index spaces, corrupting subsequent token text. Fixed by converting via `String.codePointCount` before indexing.

## [0.1.0] - 2026-07-26

### Added

- Initial scaffold: Maven multi-module JPMS project (`vietnamese-tokenizer-core`, `vietnamese-tokenizer-dicts`).
- Public API: `Token`, `TokenizeOption`, `VnTokenizer` facade (tokenization stubs deferred to a later change).
- Internal data structures: `DoubleArrayTrie`, `WordTrie`, `SyllableTrie`, `BigramScores`.
- `VnNormalizer`: Vietnamese Unicode NFD→NFC normalizer.
- CI workflow (GitHub Actions, Temurin 21, `mvn -B clean verify`).
- Probity-based TDD enforcement, project docs, and dev tooling config.

[Unreleased]: https://github.com/tylern91/vietnamese-tokenizer/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/tylern91/vietnamese-tokenizer/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/tylern91/vietnamese-tokenizer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/tylern91/vietnamese-tokenizer/releases/tag/v0.1.0
