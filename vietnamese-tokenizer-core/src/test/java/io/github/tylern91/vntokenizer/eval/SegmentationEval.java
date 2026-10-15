package io.github.tylern91.vntokenizer.eval;

import io.github.tylern91.vntokenizer.Token;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Test-scope segmentation scorer over CoNLL-U gold data. */
public final class SegmentationEval {

    private SegmentationEval() {}

    /** A half-open codepoint span {@code [start, end)}. */
    public record Span(int start, int end) {}

    /** One gold sentence: its FORM column, in order. */
    public record Sentence(List<String> forms) {
        public String text() {
            throw new UnsupportedOperationException();
        }

        public Set<Span> goldSpans() {
            throw new UnsupportedOperationException();
        }
    }

    /** Micro-averaged result for one split. */
    public record Score(
            String split, int sentences, long gold, long predicted, long hit, long multiGold, long multiHit) {
        public double precision() {
            throw new UnsupportedOperationException();
        }

        public double recall() {
            throw new UnsupportedOperationException();
        }

        public double f1() {
            throw new UnsupportedOperationException();
        }

        public double multiSyllableRecall() {
            throw new UnsupportedOperationException();
        }

        public String toJson() {
            throw new UnsupportedOperationException();
        }
    }

    public static List<Sentence> parseConllu(String content) {
        throw new UnsupportedOperationException();
    }

    public static List<Sentence> parseConllu(Path file) throws IOException {
        throw new UnsupportedOperationException();
    }

    public static Score score(String split, List<Sentence> gold, Function<String, List<Token>> tokenizer) {
        throw new UnsupportedOperationException();
    }

    public static Function<String, List<Token>> everySyllableBaseline() {
        throw new UnsupportedOperationException();
    }

    public static Path repoRoot() {
        throw new UnsupportedOperationException();
    }
}
