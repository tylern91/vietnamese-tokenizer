import { defineConfig, enforceTdd, forbidContentPattern, forbidCommandPattern } from '@nizos/probity'

// Extends (never replaces) Probity's default Red-Green-Refactor rules text.
// Facts 1-2 make the gate *stricter* than the defaults: they close a live
// fail-open where a broken-build stack trace could be misread as a red.
const tddProjectFacts = (defaults) => `${defaults}

## Project facts (vietnamese-tokenizer)

1. Not a red: a failure of the form "Could not find artifact
   io.github.tylern91:vietnamese-tokenizer-dicts" is a broken build
   environment, not a failing test. Never accept it as evidence of red.
2. Not a red: a [WARNING]-only or zero-test run ("Tests run: 0") is not
   evidence of red either.
3. Stub convention: throw new UnsupportedOperationException(), matching the
   existing convention already used in this codebase.
4. module-info.java is a JPMS declaration -- requires/exports edits are
   scaffolding with no assertable behavior.
5. vietnamese-tokenizer-dicts has no src/test/java at all, so a write to its
   module-info.java can never be justified by a test.
6. The canonical red-observation command is: mvn -q -Dtest=<ClassName> test
   run from the repo root. Do not add -pl vietnamese-tokenizer-core -- that
   excludes vietnamese-tokenizer-dicts from the reactor and forces a
   repository lookup that fails unless dicts was installed to ~/.m2 first.
`

export default defineConfig({
  rules: [
    // Deterministic invariant: internal.* packages must never be exported.
    // Pure regex -- no LLM call, no added latency. Covers all five current
    // internal packages (decode, dict, norm, score, trie) and any future
    // sibling, since they share the internal. prefix. Ordered BEFORE
    // enforceTdd: module-info.java sits under src/main/java/**, so both
    // blocks match it, and the engine runs entries in array order (see
    // engine.js resolveRules/evaluate -- first match short-circuits). This
    // block first means an exports-internal violation blocks on regex
    // alone, with no AI validator call at all.
    {
      files: ['**/module-info.java'],
      rules: [
        forbidContentPattern({
          match: /exports\s+io\.github\.tylern91\.vntokenizer\.internal/,
          reason:
            'internal.* packages are implementation detail and must stay unexported (.claude/CLAUDE.md)',
        }),
      ],
    },

    // TDD gate: real production code only. Tuned window (20 events /
    // 12000 chars) so a realistic red-stub-green loop with a little
    // exploration in between doesn't scroll the red out of context.
    {
      files: ['**/src/main/java/**'],
      rules: [
        enforceTdd({
          maxEvents: 20,
          maxContentChars: 12000,
          instructions: tddProjectFacts,
        }),
      ],
    },

    // Global command guards -- flat rules, no files filter (forbidCommandPattern
    // self-filters to command actions and ignores block-level files anyway).
    forbidCommandPattern({
      match: /--no-gpg-sign/,
      reason: 'commits and tags must be GPG-signed',
    }),
    forbidCommandPattern({
      match: /git\s+commit[^|;&]*--no-verify/,
      reason: 'do not bypass commit hooks',
    }),
  ],
})
