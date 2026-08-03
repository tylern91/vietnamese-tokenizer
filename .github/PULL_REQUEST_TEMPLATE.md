## Summary

<!-- 1-3 bullets: what changed and why. -->

-

## Test plan

<!-- Every box here must be verifiable NOW, before merge. Don't add a box for
     something only provable after merge (e.g. "next release publishes cleanly") —
     it will permanently block check-pr.sh's gate. Put forward-looking notes below
     this section instead. -->

- [ ] `mvn -B clean verify` passes
- [ ] New or changed behavior is covered by a test
- [ ] `CHANGELOG.md` updated under `[Unreleased]`

---

Before merging: apply a semver label (`major`/`minor`/`patch`), or `skip-release` if this
shouldn't trigger a release. Target `main`.
