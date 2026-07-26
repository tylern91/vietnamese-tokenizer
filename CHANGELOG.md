# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-07-26

### Added

- Initial Phase 1 scaffold: Maven multi-module JPMS project (`vietnamese-tokenizer-core`, `vietnamese-tokenizer-dicts`).
- Public API: `Token`, `TokenizeOption`, `VnTokenizer` facade (tokenization stubs deferred to Phase 4).
- Internal data structures: `DoubleArrayTrie`, `WordTrie`, `SyllableTrie`, `BigramScores`.
- `VnNormalizer`: Vietnamese Unicode NFD→NFC normalizer.
- CI workflow (GitHub Actions, Temurin 21, `mvn -B clean verify`).
- Probity-based TDD enforcement, project docs, and dev tooling config.

[Unreleased]: https://github.com/tylern91/vietnamese-tokenizer/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/tylern91/vietnamese-tokenizer/releases/tag/v0.1.0
