# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-07-29

### Added

- Real dictionary-based Viterbi word segmentation, replacing the Phase 1 tokenization stubs.
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

## [0.1.0] - 2026-07-26

### Added

- Initial Phase 1 scaffold: Maven multi-module JPMS project (`vietnamese-tokenizer-core`, `vietnamese-tokenizer-dicts`).
- Public API: `Token`, `TokenizeOption`, `VnTokenizer` facade (tokenization stubs deferred to Phase 4).
- Internal data structures: `DoubleArrayTrie`, `WordTrie`, `SyllableTrie`, `BigramScores`.
- `VnNormalizer`: Vietnamese Unicode NFD→NFC normalizer.
- CI workflow (GitHub Actions, Temurin 21, `mvn -B clean verify`).
- Probity-based TDD enforcement, project docs, and dev tooling config.

[Unreleased]: https://github.com/tylern91/vietnamese-tokenizer/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/tylern91/vietnamese-tokenizer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/tylern91/vietnamese-tokenizer/releases/tag/v0.1.0
