# Evaluation data attribution

The segmentation eval harness (`SegmentationEval`, test scope) scores the tokenizer against the
**UD_Vietnamese-VTB** treebank. The data is downloaded on demand by `scripts/fetch-eval-data.sh`
into `eval-data/`, which is gitignored: it is never committed to this repository and never
packaged into any published artifact. This file carries the attribution that would otherwise
live next to the data.

- **Dataset:** Universal Dependencies, UD_Vietnamese-VTB
  (<https://github.com/UniversalDependencies/UD_Vietnamese-VTB>), files
  `vi_vtb-ud-train.conllu`, `vi_vtb-ud-dev.conllu` and `vi_vtb-ud-test.conllu`.
- **Pinned version:** commit `3c971fd1c5d03561e00b267d3d0057b81a9bea84`, each file verified
  against a sha256 hardcoded in `scripts/fetch-eval-data.sh`.
- **License:** [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **Origin:** a conversion of the constituent treebank created in the VLSP project. Source
  reference: Phuong-Thai Nguyen, Xuan-Luong Vu, Thi-Minh-Huyen Nguyen, Van-Hiep Nguyen and
  Hong-Phuong Le (2009), *Building a Large Syntactically-Annotated Corpus of Vietnamese*,
  Proceedings of the Third Linguistic Annotation Workshop, ACL-IJCNLP 2009, pp. 182-185.
  UD conversion contributors: Nguyễn Thị Lương, Hà Mỹ Linh, Lê Hồng Phương, Nguyễn Thị Minh Huyền.
- **Changes:** none. The files are used unmodified; the harness only reads the FORM column.

Scores produced from this data are an internal regression baseline (`eval-baseline.json`), not a
benchmark comparable to published VLSP results.
