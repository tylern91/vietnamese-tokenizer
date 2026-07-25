# vietnamese-tokenizer

[![CI](https://github.com/tylern91/vietnamese-tokenizer/actions/workflows/ci.yml/badge.svg)](https://github.com/tylern91/vietnamese-tokenizer/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

High-performance Vietnamese word tokenizer — pure Java 21+, zero native dependencies.

## Requirements

- Java 21+

## Build

```bash
mvn -B clean verify
```

> Local `mvn test` on just `vietnamese-tokenizer-core` requires a one-time reactor
> install first (see `.claude/CLAUDE.md` for details) — `mvn -B clean verify` from
> the repo root is unaffected and is what CI runs.

## Modules

- **`vietnamese-tokenizer-core`** — the public API (`io.github.tylern91.vntokenizer`:
  `VnTokenizer`, `Token`, `TokenizeOption`) plus internal data structures
  (`internal.trie`, `internal.score`, `internal.norm`) used by the tokenizer.
- **`vietnamese-tokenizer-dicts`** — bundled dictionary data, packaged as a jar
  consumed by `core`.

## Usage

```java
VnTokenizer tokenizer = VnTokenizer.getInstance();
List<Token> tokens = tokenizer.tokenize("Tiếng Việt");
```

> `tokenize()` is currently a stub (`UnsupportedOperationException`) — the Viterbi
> decode pipeline that powers real tokenization lands in Phase 4.

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE).
