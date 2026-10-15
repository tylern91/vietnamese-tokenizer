package io.github.tylern91.vntokenizer.eval;

import io.github.tylern91.vntokenizer.Token;
import io.github.tylern91.vntokenizer.VnTokenizer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Test-scope word-segmentation scorer over CoNLL-U gold data (UD_Vietnamese-VTB).
 *
 * <p>Metric: for each gold sentence the FORMs are joined by single spaces into the tokenizer
 * input; a gold word is a codepoint span {@code [start, end)} in that text. The tokenizer's
 * non-{@link Token.Type#SPACE} tokens are the predicted spans. A predicted span is a hit when
 * it equals a gold span exactly. Precision, recall and F1 are micro-averaged over the split.
 * Multi-syllable recall is the fraction of gold words containing a space whose exact span is
 * predicted.
 *
 * <p>The scorer takes any {@code Function<String, List<Token>>}, so tuned or alternative
 * tokenizers can be scored without depending on internals. As a program:
 * {@code SegmentationEval [dataDir] [split ...] [--baseline]} prints one JSON line per split
 * (default splits: dev, test; default dataDir: {@code <repo root>/eval-data}).
 */
public final class SegmentationEval {

    private static final Pattern WORD_ID = Pattern.compile("[0-9]+");
    private static final Pattern RANGE_OR_EMPTY_NODE_ID = Pattern.compile("[0-9]+[-.][0-9]+");
    private static final int JSON_DECIMALS = 6;

    private SegmentationEval() {}

    /** A half-open codepoint span {@code [start, end)}. */
    public record Span(int start, int end) {}

    /** One gold sentence: its FORM column, in order. */
    public record Sentence(List<String> forms) {
        public Sentence {
            forms = List.copyOf(forms);
        }

        /** The tokenizer input: FORMs joined by a single space. */
        public String text() {
            return String.join(" ", forms);
        }

        /** Gold word spans in {@link #text()}, in codepoints. */
        public Set<Span> goldSpans() {
            Set<Span> spans = new HashSet<>();
            int pos = 0;
            for (String form : forms) {
                int length = form.codePointCount(0, form.length());
                spans.add(new Span(pos, pos + length));
                pos += length + 1;
            }
            return spans;
        }
    }

    /** Micro-averaged result for one split. */
    public record Score(
            String split, int sentences, long gold, long predicted, long hit, long multiGold, long multiHit) {

        public double precision() {
            return predicted == 0 ? 0.0 : (double) hit / predicted;
        }

        public double recall() {
            return gold == 0 ? 0.0 : (double) hit / gold;
        }

        public double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0.0 ? 0.0 : 2 * p * r / (p + r);
        }

        public double multiSyllableRecall() {
            return multiGold == 0 ? 0.0 : (double) multiHit / multiGold;
        }

        /** One-line JSON; numbers are rounded to six decimals with trailing zeros dropped. */
        public String toJson() {
            return "{\"split\":\"" + split + "\",\"sentences\":" + sentences
                    + ",\"precision\":" + num(precision())
                    + ",\"recall\":" + num(recall())
                    + ",\"f1\":" + num(f1())
                    + ",\"multiSyllableRecall\":" + num(multiSyllableRecall()) + "}";
        }

        private static String num(double value) {
            return BigDecimal.valueOf(value)
                    .setScale(JSON_DECIMALS, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
    }

    /**
     * Parses CoNLL-U text. Comment lines ({@code #}), multiword-token range lines ({@code 1-2})
     * and empty-node lines ({@code 1.1}) are skipped; the FORM column of each word line is kept;
     * a blank line ends a sentence. Sentences with no word lines are dropped.
     */
    public static List<Sentence> parseConllu(String content) {
        List<Sentence> sentences = new ArrayList<>();
        List<String> forms = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                flush(sentences, forms);
            } else if (!line.startsWith("#")) {
                String[] cols = line.split("\t", -1);
                if (cols.length < 2) {
                    throw new IllegalArgumentException("CoNLL-U line " + (i + 1) + " has no FORM column: " + line);
                }
                String id = cols[0];
                if (WORD_ID.matcher(id).matches()) {
                    forms.add(cols[1]);
                } else if (!RANGE_OR_EMPTY_NODE_ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("CoNLL-U line " + (i + 1) + " has an unrecognised ID: " + id);
                }
            }
        }
        flush(sentences, forms);
        return sentences;
    }

    public static List<Sentence> parseConllu(Path file) throws IOException {
        return parseConllu(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static void flush(List<Sentence> sentences, List<String> forms) {
        if (!forms.isEmpty()) {
            sentences.add(new Sentence(forms));
            forms.clear();
        }
    }

    /** Scores {@code tokenizer} on {@code gold}; see the class comment for the metric. */
    public static Score score(String split, List<Sentence> gold, Function<String, List<Token>> tokenizer) {
        long goldCount = 0;
        long predictedCount = 0;
        long hit = 0;
        long multiGold = 0;
        long multiHit = 0;
        for (Sentence sentence : gold) {
            Set<Span> goldSpans = sentence.goldSpans();
            Set<Span> predicted = new HashSet<>();
            for (Token token : tokenizer.apply(sentence.text())) {
                if (token.type() != Token.Type.SPACE) {
                    Span span = new Span(token.start(), token.end());
                    predicted.add(span);
                    predictedCount++;
                    if (goldSpans.contains(span)) {
                        hit++;
                    }
                }
            }
            goldCount += goldSpans.size();
            int pos = 0;
            for (String form : sentence.forms()) {
                int length = form.codePointCount(0, form.length());
                if (form.indexOf(' ') >= 0) {
                    multiGold++;
                    if (predicted.contains(new Span(pos, pos + length))) {
                        multiHit++;
                    }
                }
                pos += length + 1;
            }
        }
        return new Score(split, gold.size(), goldCount, predictedCount, hit, multiGold, multiHit);
    }

    /** The trivial baseline: every space-separated syllable is its own word. */
    public static Function<String, List<Token>> everySyllableBaseline() {
        return text -> {
            List<Token> tokens = new ArrayList<>();
            int start = -1;
            int pos = 0;
            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                if (cp == ' ') {
                    if (start >= 0) {
                        tokens.add(new Token(text.substring(offsetOf(text, start), i), Token.Type.WORD, start, pos));
                        start = -1;
                    }
                } else if (start < 0) {
                    start = pos;
                }
                i += Character.charCount(cp);
                pos++;
            }
            if (start >= 0) {
                tokens.add(new Token(text.substring(offsetOf(text, start)), Token.Type.WORD, start, pos));
            }
            return tokens;
        };
    }

    private static int offsetOf(String text, int codepointIndex) {
        return text.offsetByCodePoints(0, codepointIndex);
    }

    /**
     * The repository root, independent of the Maven module's working directory: the
     * {@code eval.root} system property when set (the {@code eval} profile sets it), otherwise
     * the nearest ancestor of the working directory that holds {@code eval-baseline.json}.
     */
    public static Path repoRoot() {
        String configured = System.getProperty("eval.root");
        if (configured != null && !configured.isBlank() && !configured.contains("${")) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("eval-baseline.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "cannot locate the repo root: no eval-baseline.json above " + Paths.get("").toAbsolutePath()
                        + " and no -Deval.root");
    }

    public static void main(String[] args) throws IOException {
        List<String> positional = new ArrayList<>();
        boolean baseline = false;
        for (String arg : args) {
            if (arg.equals("--baseline")) {
                baseline = true;
            } else {
                positional.add(arg);
            }
        }
        Path dataDir = positional.isEmpty() ? repoRoot().resolve("eval-data") : Paths.get(positional.get(0));
        List<String> splits = positional.size() > 1 ? positional.subList(1, positional.size()) : List.of("dev", "test");
        Function<String, List<Token>> tokenizer =
                baseline ? everySyllableBaseline() : VnTokenizer.getInstance()::tokenize;
        for (String split : splits) {
            List<Sentence> gold = parseConllu(dataDir.resolve("vi_vtb-ud-" + split + ".conllu"));
            System.out.println(score(split, gold, tokenizer).toJson());
        }
    }
}
