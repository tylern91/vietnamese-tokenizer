# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
