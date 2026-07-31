# vietnamese-tokenizer

[![CI](https://github.com/tylern91/vietnamese-tokenizer/actions/workflows/ci.yml/badge.svg)](https://github.com/tylern91/vietnamese-tokenizer/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.tylern91/vietnamese-tokenizer-core)](https://central.sonatype.com/artifact/io.github.tylern91/vietnamese-tokenizer-core)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A Vietnamese word tokenizer for the JVM — pure Java 21, no native dependencies. It segments
Vietnamese text into words using a dictionary-based Viterbi decoder over a corpus derived from
Vietnamese Wiktionary and the UVW-2026 dataset, rather than splitting on whitespace (which
under-segments Vietnamese, since words are commonly multi-syllable but written space-separated).

The public API is three types in `io.github.tylern91.vntokenizer`: `VnTokenizer`, `Token`, and
`TokenizeOption`.

## Example output

```java
VnTokenizer tokenizer = VnTokenizer.getInstance();
List<Token> tokens = tokenizer.tokenize("Xin chào Việt Nam");
```

```
Token[text=Xin chào, type=WORD, start=0, end=8]
Token[text= , type=SPACE, start=8, end=9]
Token[text=Việt Nam, type=WORD, start=9, end=17]
```

`Xin chào` ("hello") and `Việt Nam` ("Vietnam") are each recognized as single multi-syllable
words, not four independent tokens.

## Installation

**Maven:**

```xml
<dependency>
    <groupId>io.github.tylern91</groupId>
    <artifactId>vietnamese-tokenizer-core</artifactId>
    <version>0.2.0</version>
</dependency>
```

**Gradle:**

```kotlin
implementation("io.github.tylern91:vietnamese-tokenizer-core:0.2.0")
```

Depending on `vietnamese-tokenizer-core` pulls in `vietnamese-tokenizer-dicts` (the bundled
dictionary data) transitively — no separate dependency needed.

> These coordinates go live on Maven Central with the project's first published release; until
> then, build from source (below).

## Usage

```java
VnTokenizer tokenizer = VnTokenizer.getInstance();
List<Token> tokens = tokenizer.tokenize("Xin chào Việt Nam");
List<String> words = tokenizer.tokenizeToStrings("Xin chào Việt Nam");
// words == ["Xin chào", " ", "Việt Nam"]
```

Each `Token` is a record: `text()`, `type()` (`WORD`, `NUMBER`, `PUNCT`, or `SPACE`), and
codepoint offsets `start()`/`end()` into the input.

### Modes

`tokenize(String, TokenizeOption)` takes a second mode argument for text containing URLs or
hostnames, which would otherwise get word-segmented like ordinary text:

```java
tokenizer.tokenize("xem tại https://example.com/path hôm nay", TokenizeOption.URL);
// -> "https://example.com/path" kept as one WORD token; surrounding text still segmented normally
```

- `TokenizeOption.NORMAL` (default) — plain word segmentation.
- `TokenizeOption.URL` — `http://`/`https://`/`www.` matches are kept intact as a single token.
- `TokenizeOption.HOST` — bare hostnames (e.g. `example.com`) are kept intact as a single token.

### Using your own dictionary data

`VnTokenizer.getInstance(Path dictDir)` loads `words.txt`, `syllables.txt`, and `bigrams.txt` from
an arbitrary directory instead of the bundled classpath resources, for callers who want to swap in
a different or extended vocabulary.

## Requirements

Java 21 or later. The published module (`io.github.tylern91.vntokenizer`) is a JPMS module; module
consumers need `requires io.github.tylern91.vntokenizer;` in their own `module-info.java`.
Classpath (non-modular) consumers need nothing extra.

## Build from source

```bash
mvn -B clean verify
```

## Troubleshooting

**JPMS `requires` missing.** If a module-path consumer gets `module not found` or an unresolved
reference to `io.github.tylern91.vntokenizer`, add `requires io.github.tylern91.vntokenizer;` to
that module's `module-info.java`. Classpath consumers are unaffected.

## Thanks to

The bundled dictionary data (`words.txt.gz`, `syllables.txt.gz`, `bigrams.txt.gz` in
`vietnamese-tokenizer-dicts`) is derived from:

- [Vietnamese Wiktionary](https://vi.wiktionary.org/) (viwiktionary dump, 2026-07-01) — page
  titles used as a seed candidate list for multi-syllable compound words.
- [`undertheseanlp/UVW-2026`](https://huggingface.co/datasets/undertheseanlp/UVW-2026)
  (revision `a0a79294`) — used to build the syllable inventory and word/bigram frequency counts.

Both sources are licensed CC BY-SA 4.0. Full attribution, source URLs, and license details are in
[`vietnamese-tokenizer-dicts`'s `NOTICE`](vietnamese-tokenizer-dicts/src/main/resources/io/github/tylern91/vntokenizer/dicts/NOTICE).

## License

`vietnamese-tokenizer-core` is licensed under the [Apache License, Version 2.0](LICENSE).

`vietnamese-tokenizer-dicts` — the bundled dictionary data only — is licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) as a share-alike derivative of the
sources above; see the "Thanks to" section and its `NOTICE` file.
