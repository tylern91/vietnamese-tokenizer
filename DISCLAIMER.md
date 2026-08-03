# Disclaimer

## No warranty

This software is provided "as is", without warranty of any kind, express or implied, to the
extent permitted by the Apache License, Version 2.0 under which it is distributed. See `LICENSE`.

## Limitation of liability

In no event shall the authors or contributors be liable for any claim, damages, or other
liability arising from the use of this software, as set out in Apache License 2.0 §8.

## Third-party dependencies

Any third-party dependency this project uses retains its own license. This project's own
Apache-2.0 license does not extend to or relicense third-party code.

## Dictionary data licensing

The dictionary data bundled in `vietnamese-tokenizer-dicts` (`words.txt.gz`, `syllables.txt.gz`,
`bigrams.txt.gz`) is derived from:

- The Vietnamese Wiktionary dump (dump run 20260701)
- The UVW-2026 dataset (commit `a0a79294e4568137e25828bb3f2a4cde8546e1fb`)

Both sources are licensed **CC BY-SA 4.0**, not Apache-2.0 — see
`vietnamese-tokenizer-dicts/src/main/resources/io/github/tylern91/vntokenizer/dicts/NOTICE` for
the full attribution text. If you redistribute this dictionary data, or a derivative of it, on
its own or bundled with other work, CC BY-SA 4.0 obligates you to preserve attribution and to
license your derivative under the same or a compatible share-alike license. This obligation
applies to the dictionary data specifically; it does not extend to the Apache-2.0-licensed Java
code that consumes it.

## Accuracy

Word segmentation is produced by a statistical model (dictionary lookup plus Viterbi decoding)
and is not guaranteed to be linguistically correct for any given input. Out-of-vocabulary text,
ambiguous segmentations, and dictionary gaps can all produce output a human reader would
segment differently.

## No telemetry

This library collects and transmits nothing. It makes no network calls, phones home no usage
data, and reads only the dictionary resources it is configured to load (bundled on the classpath,
or from a filesystem path you supply).
