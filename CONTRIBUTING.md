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

## TDD gate

Writes to `**/src/main/java/**` are gated by Probity's `enforceTdd()`: a failing JUnit test must
already exist before the corresponding main-source class can be written (red before green).
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

## Dictionary data

Changes to `vietnamese-tokenizer-dicts` must preserve the CC BY-SA 4.0 attribution in that
module's `NOTICE` file (`src/main/resources/io/github/tylern91/vntokenizer/dicts/NOTICE`). See
`DISCLAIMER.md` for what that license obligates downstream.
