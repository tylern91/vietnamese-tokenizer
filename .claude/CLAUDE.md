# vietnamese-tokenizer

Multi-module Maven / JPMS Java 21 library. Module: `io.github.tylern91.vntokenizer`.

## Layout

- `vietnamese-tokenizer-core` — public API package `io.github.tylern91.vntokenizer`
  (`VnTokenizer`, `Token`, `TokenizeOption`) — the only package exported by
  `module-info.java`. Internal data structures live under
  `io.github.tylern91.vntokenizer.internal.{decode,dict,norm,score,trie}` and
  must **not** be added to `module-info.java` exports — enforced by
  `probity.config.mjs`'s `forbidContentPattern` guard, not just this note.
- `vietnamese-tokenizer-dicts` — bundled dictionary data, packaged as a jar
  consumed by `core` at compile scope. Has no `src/test/java` at all.

## Build

Full reactor (what CI runs):

```bash
mvn -B clean verify
```

Local iteration on a single test class — Maven resolves `dicts` in-reactor
directly, no bootstrap install required:

```bash
mvn -q -Dtest=WordTrieTest test
```

Do **not** add `-pl vietnamese-tokenizer-core` to that command: `-pl` excludes
`dicts` from the reactor and forces a repository lookup, which fails unless
`dicts` was `install`ed to `~/.m2` beforehand. Running from the repo root with
no `-pl` avoids that — `dicts` has no test sources, so the `-Dtest` filter
simply doesn't touch it.

## ATDD

Acceptance criteria (ACs) drive the work: one `[AC-N]` test per AC, written and seen RED before
any implementation. Cycle: Discuss (the AC list) -> Distill (write the `[AC-N]` tests, confirm
RED for the right reason) -> Develop (minimal implementation) -> Demo (green, revert-verified,
ledger row).

- **Library ACs** are JUnit 5 `*AcceptanceTest` classes in
  `vietnamese-tokenizer-core/src/test/java/io/github/tylern91/vntokenizer/acceptance/`, one
  `@Test` per AC with `@DisplayName("[AC-N] ...")`. Run them with
  `mvn -q -Dtest='*AcceptanceTest' test` (repo root, no `-pl`).
- **Script and workflow ACs** are BATS files in `tests/acceptance/<slug>.bats` with
  `@test "[AC-N] ..."` names. Run everything with `bats -r tests/`.
- **Revert ledger**: every AC needs a row in `tests/acceptance/revert-ledger.tsv` (`suite`, `ac`,
  `verified_sha`, `date`; suite is the test file's basename). CI's `revert-evidence` job runs
  `scripts/check-revert-evidence.sh` and fails a PR that adds an `[AC-N]` test with no row.
- **Below the acceptance tier**, write a unit test only where it guards a branch no AC reaches.

Revert-verify each AC without `git stash`, after committing the implementation:

1. `git checkout HEAD~1 -- <impl files>` and confirm the AC FAILs.
2. `git checkout HEAD -- <impl files>` and confirm it PASSes.
3. Append the ledger row, with the implementation commit's sha as `verified_sha`.

## TDD gate (Probity)

Writes to `**/src/main/java/**` are gated by Probity's `enforceTdd()`: a
failing JUnit test must already exist in the session transcript before the
corresponding main-source class can be written (red before green). A failing
`[AC-N]` acceptance test that fails for the right reason is that red.
Test-file writes are never gated. Config: `probity.config.mjs`.

Three things the gate itself can't enforce:

- **Sub-agents cannot satisfy the gate.** Probity reads only the main
  session's transcript, and a sub-agent's genuine red doesn't count no matter
  how real it is — this isn't a config option. Sub-agents may write tests,
  explore, and design; the **main session** must observe the red and perform
  the `src/main/java/**` write itself.
- **A brand-new symbol costs two gated writes**: a compiling stub (e.g.
  `throw new UnsupportedOperationException()`, the existing convention here)
  to reach a clean red, then the real implementation. Replacing the body of a
  method that already exists and compiles needs only one.
- **One test per behavior before a bundled implementation.** The scope check
  blocks otherwise-correct logic that no currently-failing test drives — land
  the test first, even when the full behavior set is already clear.
