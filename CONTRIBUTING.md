# Contributing to vietnamese-tokenizer

Participation in this project is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

## What this is

A dictionary-based Vietnamese word tokenizer for Java 21+: Viterbi word segmentation over
bundled Wiktionary + UVW-2026 derived dictionaries, with no runtime dependencies beyond those
dictionaries.

## Design principles

- **The public API stays minimal.** `VnTokenizer`, `Token`, and `TokenizeOption` — package
  `io.github.tylern91.vntokenizer` — are the only exports declared in `module-info.java`.
- **`internal.*` packages never get exported.** Anything under
  `io.github.tylern91.vntokenizer.internal.*` is an implementation detail and must not be added
  to `module-info.java`'s `exports` clause.
- **Correctness on real Vietnamese text over micro-optimization.** Segmentation quality against
  actual usage matters more than shaving allocations.
- **No runtime dependencies beyond the bundled dictionaries.** `vietnamese-tokenizer-core` should
  not gain a new runtime dependency without a strong reason.

## Build and test

Full reactor (what CI runs):

```bash
mvn -B clean verify
```

Local iteration on a single test class — run from the repo root, no `-pl`:

```bash
mvn -q -Dtest=WordTrieTest test
```

Do **not** add `-pl vietnamese-tokenizer-core` to that command: `-pl` excludes
`vietnamese-tokenizer-dicts` from the reactor and forces a repository lookup, which fails unless
`dicts` was `install`ed to `~/.m2` beforehand. Running from the repo root with no `-pl` avoids
that — `dicts` has no test sources, so the `-Dtest` filter simply doesn't touch it.

## Acceptance tests (ATDD)

Work starts from acceptance criteria (ACs). Each AC becomes one test named `[AC-N] ...`, written
and seen to fail before any implementation:

1. **Discuss**: agree the numbered AC list.
2. **Distill**: write one `[AC-N]` test per AC and confirm each fails for the right reason.
   - Java library behavior: a JUnit 5 `*AcceptanceTest` class under
     `vietnamese-tokenizer-core/src/test/java/io/github/tylern91/vntokenizer/acceptance/`, with
     `@DisplayName("[AC-N] ...")` on each `@Test`. Run it with
     `mvn -q -Dtest='*AcceptanceTest' test`.
   - Scripts and workflows: a BATS file in `tests/acceptance/` with `@test "[AC-N] ..."` names.
     Run all shell tests with `bats -r tests/`.
3. **Develop**: the minimal change that turns them green. Below the acceptance tier, add a unit
   test only where it guards a branch no AC reaches.
4. **Demo**: revert-verify each AC and record it in `tests/acceptance/revert-ledger.tsv`.

Revert-verify without `git stash`, after committing the implementation:

```bash
git checkout HEAD~1 -- <implementation files>   # the AC must FAIL
git checkout HEAD -- <implementation files>     # the AC must PASS again
```

Then append one row per AC to the ledger (tab-separated: `suite`, `ac`, `verified_sha`, `date`),
where `suite` is the test file's basename and `verified_sha` is the implementation commit. CI's
`revert-evidence` job (`scripts/check-revert-evidence.sh`) fails a pull request that adds an
`[AC-N]` test with no matching row.

## TDD gate

Writes to `**/src/main/java/**` are gated by Probity's `enforceTdd()`: a failing JUnit test must
already exist before the corresponding main-source class can be written (red before green). A
failing `[AC-N]` acceptance test counts as that red.
Test-file writes are never gated. A brand-new symbol typically needs a compiling stub first
(e.g. `throw new UnsupportedOperationException()`) to reach a clean red, then the real
implementation as a second change. Replacing the body of a method that already exists and
compiles needs only one change. See `.claude/CLAUDE.md` for the full detail if you're using
Claude Code against this repo.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): short description`.
The type drives the release pipeline's semver bump — `feat` → minor, everything else → patch,
`!` suffix or a `BREAKING CHANGE:` footer → major.

## Sign-off (DCO)

Every commit must carry a `Signed-off-by:` trailer, added automatically with `git commit -s`.
This certifies you have the right to submit the contribution under the project's license — the
[Developer Certificate of Origin 1.1](https://developercertificate.org/), reproduced below.
There is no CLA and no external signing service.

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

**Note on squash merges:** this repo squash-merges PRs, so the `Signed-off-by:` trailer that
survives onto `main` is whatever ends up in the final squash commit message — not necessarily
every per-commit trailer from your branch. The CI check gates the PR; it doesn't guarantee every
intermediate commit's trailer reaches `main`.

If you also GPG-sign commits, note that `-s` (sign-off, a trailer) and `-S` (GPG signature) are
orthogonal — `git commit -s -S` applies both.

## Branch naming

`feature/`, `fix/`, `chore/`, `ci/`, `docs/` followed by a kebab-case slug, branched from `main`.
This repo has no `develop` branch.

## Pull requests

- One concern per PR.
- Apply a semver label (`major`/`minor`/`patch`), or `skip-release` to bypass the release
  pipeline for config/doc-only changes.
- Add CHANGELOG bullets under `[Unreleased]`.
- Every box in the PR template's `## Test plan` section must be checked before merge —
  `check-pr.sh` enforces this and it is not advisory. Only include a checkbox for something
  verifiable before merge; forward-looking notes belong outside that section.

## Release recovery

The release pipeline (`.github/workflows/release.yml`) normally runs once, on a PR merging to
`main`. If `publish` fails or is left stuck after the tag and GitHub Release already exist (e.g.
Central rejected the deploy, or the run was cancelled before `publish` finished), re-running the
same workflow via GitHub's "Re-run all jobs" works — the idempotency guard recognizes the existing
tag and lets `publish` retry without recreating the tag or release.

If that isn't available (the run was deleted, or you need to retry from a different commit),
dispatch the workflow manually instead:

```bash
gh workflow run release.yml -f tag=v0.2.2
```

This only retries `publish` for a tag that **already exists** — it never derives a version or
creates a new tag/release, and it hard-fails if the given tag isn't found.

## Dictionary data

Changes to `vietnamese-tokenizer-dicts` must preserve the CC BY-SA 4.0 attribution in that
module's `NOTICE` file (`src/main/resources/io/github/tylern91/vntokenizer/dicts/NOTICE`). See
`DISCLAIMER.md` for what that license obligates downstream.
