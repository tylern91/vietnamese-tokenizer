# vietnamese-tokenizer

Multi-module Maven / JPMS Java 21 library. Module: `io.github.tylern91.vntokenizer`.

## Layout

- `vietnamese-tokenizer-core` — public API package `io.github.tylern91.vntokenizer`
  (`VnTokenizer`, `Token`, `TokenizeOption`) — the only package exported by
  `module-info.java`. Internal data structures live under
  `io.github.tylern91.vntokenizer.internal.{trie,score,norm}` and must **not**
  be added to `module-info.java` exports.
- `vietnamese-tokenizer-dicts` — bundled dictionary data, packaged as a jar
  consumed by `core` at test scope.

## Build

Full reactor (what CI runs):

```bash
mvn -B clean verify
```

Local iteration on just `core`: `mvn test` alone can't resolve the placeholder
`dicts` jar. One-time per session:

```bash
mvn -q -N install
mvn -q -pl vietnamese-tokenizer-dicts install
```

Then loop with:

```bash
mvn -q -pl vietnamese-tokenizer-core test
```

## TDD (Probity)

Writes to `**/src/main/java/**` are gated by Probity's `enforceTdd()`: a
failing JUnit test must already exist in the session transcript before the
corresponding main-source class can be written (red before green). Test-file
writes are never gated. Config: `probity.config.mjs`.
