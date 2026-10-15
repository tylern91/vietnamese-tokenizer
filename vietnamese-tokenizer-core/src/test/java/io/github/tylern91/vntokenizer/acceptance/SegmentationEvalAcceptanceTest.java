package io.github.tylern91.vntokenizer.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tylern91.vntokenizer.Token;
import io.github.tylern91.vntokenizer.Token.Type;
import io.github.tylern91.vntokenizer.VnTokenizer;
import io.github.tylern91.vntokenizer.eval.SegmentationEval;
import io.github.tylern91.vntokenizer.eval.SegmentationEval.Score;
import io.github.tylern91.vntokenizer.eval.SegmentationEval.Sentence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the segmentation eval harness (PR 2). AC-3 is the no-regression gate on
 * the UD_Vietnamese-VTB dev split; it needs {@code scripts/fetch-eval-data.sh} to have run.
 */
class SegmentationEvalAcceptanceTest {

    private static final double EXACT = 1e-12;

    // Three gold sentences (FORM column, joined by single spaces to form the tokenizer input).
    private static final String FIXTURE = """
            # sent_id = fx-1
            # text = Hà Nội là thủ đô
            1\tHà Nội\thà nội\tPROPN\t_\t_\t0\troot\t_\t_
            2\tlà\tlà\tAUX\t_\t_\t1\tcop\t_\t_
            3\tthủ đô\tthủ đô\tNOUN\t_\t_\t1\tobl\t_\t_

            # sent_id = fx-2
            1\tTôi\ttôi\tPRON\t_\t_\t0\troot\t_\t_
            2\tyêu\tyêu\tVERB\t_\t_\t1\tcsubj\t_\t_
            3\tViệt Nam\tviệt nam\tPROPN\t_\t_\t2\tobj\t_\t_
            4\t.\t.\tPUNCT\t_\t_\t1\tpunct\t_\t_

            # sent_id = fx-3
            1\tXin chào\txin chào\tINTJ\t_\t_\t0\troot\t_\t_
            """;

    private static Token w(String text, int start, int end) {
        return new Token(text, Type.WORD, start, end);
    }

    private static Token sp(int start, int end) {
        return new Token(" ", Type.SPACE, start, end);
    }

    /** Stub tokenizer: returns canned tokens per fixture text. */
    private static final Map<String, List<Token>> STUB = Map.of(
            "Hà Nội là thủ đô",
            List.of(w("Hà Nội", 0, 6), sp(6, 7), w("là", 7, 9), sp(9, 10), w("thủ", 10, 13), sp(13, 14), w("đô", 14, 16)),
            "Tôi yêu Việt Nam .",
            List.of(
                    w("Tôi", 0, 3),
                    sp(3, 4),
                    w("yêu", 4, 7),
                    sp(7, 8),
                    w("Việt", 8, 12),
                    sp(12, 13),
                    w("Nam", 13, 16),
                    sp(16, 17),
                    new Token(".", Type.PUNCT, 17, 18)),
            "Xin chào",
            List.of(w("Xin chào", 0, 8)));

    private static final Function<String, List<Token>> STUB_TOKENIZER = STUB::get;

    @Test
    @DisplayName("[AC-1] scorer reports P/R/F1/multi-syllable recall exactly as hand-computed on a 3-sentence fixture")
    void scorerMatchesHandComputedValues() {
        List<Sentence> gold = SegmentationEval.parseConllu(FIXTURE);
        assertEquals(3, gold.size());

        Score score = SegmentationEval.score("fixture", gold, STUB_TOKENIZER);

        // Gold spans (codepoints, text = forms joined by one space):
        //   S1 "Hà Nội là thủ đô": (0,6) (7,9) (10,16)            -> 3 gold, 2 multi-syllable
        //   S2 "Tôi yêu Việt Nam .": (0,3) (4,7) (8,16) (17,18)   -> 4 gold, 1 multi-syllable
        //   S3 "Xin chào": (0,8)                                   -> 1 gold, 1 multi-syllable
        // Predicted non-SPACE spans:
        //   S1: (0,6) (7,9) (10,13) (14,16)            -> 4 predicted, hits: (0,6) (7,9)       = 2
        //   S2: (0,3) (4,7) (8,12) (13,16) (17,18)     -> 5 predicted, hits: (0,3) (4,7) (17,18) = 3
        //   S3: (0,8)                                   -> 1 predicted, hit:  (0,8)             = 1
        // Totals: gold = 8, predicted = 10, hit = 6.
        //   P = 6/10 = 0.6, R = 6/8 = 0.75, F1 = 2*0.6*0.75/(0.6+0.75) = 0.9/1.35 = 2/3
        // Multi-syllable gold words: (0,6) hit, (10,16) miss, (8,16) miss, (0,8) hit -> 2 of 4 = 0.5
        assertEquals(3, score.sentences());
        assertEquals(8, score.gold());
        assertEquals(10, score.predicted());
        assertEquals(6, score.hit());
        assertEquals(0.6, score.precision(), EXACT);
        assertEquals(0.75, score.recall(), EXACT);
        assertEquals(2.0 / 3.0, score.f1(), EXACT);
        assertEquals(0.5, score.multiSyllableRecall(), EXACT);

        // Every-syllable baseline (split on spaces):
        //   S1: Hà Nội là thủ đô -> 5 predicted, 1 hit (là)
        //   S2: Tôi yêu Việt Nam . -> 5 predicted, 3 hits (Tôi yêu .)
        //   S3: Xin chào -> 2 predicted, 0 hits
        // Totals: predicted = 12, hit = 4 -> P = 4/12 = 1/3, R = 4/8 = 0.5, F1 = 2*4/(12+8) = 0.4
        // The baseline never emits a multi-syllable span, so multi-syllable recall = 0.
        Score baseline = SegmentationEval.score("fixture", gold, SegmentationEval.everySyllableBaseline());
        assertEquals(12, baseline.predicted());
        assertEquals(4, baseline.hit());
        assertEquals(1.0 / 3.0, baseline.precision(), EXACT);
        assertEquals(0.5, baseline.recall(), EXACT);
        assertEquals(0.4, baseline.f1(), EXACT);
        assertEquals(0.0, baseline.multiSyllableRecall(), EXACT);

        // One JSON line per split.
        String json = score.toJson();
        assertTrue(json.startsWith("{\"split\":\"fixture\",\"sentences\":3,"), json);
        assertTrue(json.contains("\"f1\":0.666667"), json);
        assertTrue(json.contains("\"multiSyllableRecall\":0.5"), json);
        assertTrue(!json.contains("\n"), "must be a single line");
    }

    @Test
    @DisplayName("[AC-2] comment lines, multiword-range lines and empty-node ids are ignored by the parser")
    void parserIgnoresCommentsRangesAndEmptyNodes() {
        String conllu = """
                # newdoc id = d1
                # sent_id = a-1
                # text = vamos nos yo
                1-2\tvámonos\t_\t_\t_\t_\t_\t_\t_\t_
                1\tvamos\tir\tVERB\t_\t_\t0\troot\t_\t_
                2\tnos\tnosotros\tPRON\t_\t_\t1\tobj\t_\t_
                2.1\tghost\tghost\tX\t_\t_\t_\t_\t_\t_
                3\tyo\tyo\tPRON\t_\t_\t1\tnsubj\t_\t_


                # sent_id = a-2
                # only a comment and a range follow
                1-2\tdel\t_\t_\t_\t_\t_\t_\t_\t_
                1\tde\tde\tADP\t_\t_\t0\troot\t_\t_
                2\tel\tel\tDET\t_\t_\t1\tdet\t_\t_

                # sent_id = a-3 (no forms at all)
                """;

        List<Sentence> parsed = SegmentationEval.parseConllu(conllu);

        assertEquals(
                List.of(List.of("vamos", "nos", "yo"), List.of("de", "el")),
                parsed.stream().map(Sentence::forms).toList());
        assertEquals("vamos nos yo", parsed.get(0).text());
    }

    @Test
    @DisplayName("[AC-3] UD-VTB dev F1 with the bundled tokenizer is at least the recorded eval-baseline.json floor")
    void devF1IsAtLeastTheRecordedFloor() throws IOException {
        Path root = SegmentationEval.repoRoot();
        Path baselineFile = root.resolve("eval-baseline.json");
        assertTrue(Files.isRegularFile(baselineFile), "missing tracked baseline file: " + baselineFile);
        double floor = readJsonNumber(baselineFile, "dev_f1") - readJsonNumber(baselineFile, "tolerance");

        Path dev = root.resolve("eval-data").resolve("vi_vtb-ud-dev.conllu");
        if (!Files.isRegularFile(dev)) {
            String msg = "UD-VTB dev split not found at " + dev + " (run scripts/fetch-eval-data.sh)";
            if (Boolean.parseBoolean(System.getProperty("eval.required"))) {
                fail(msg + "; eval.required=true forbids skipping");
            }
            assumeTrue(false, msg);
        }

        Score score = SegmentationEval.score(
                "dev", SegmentationEval.parseConllu(dev), VnTokenizer.getInstance()::tokenize);
        System.out.println(score.toJson());

        assertTrue(
                score.f1() >= floor,
                "dev F1 " + score.f1() + " fell below the recorded floor " + floor + " (" + baselineFile + ")");
    }

    private static double readJsonNumber(Path file, String key) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)")
                .matcher(json);
        assertTrue(m.find(), "no numeric \"" + key + "\" in " + file);
        return Double.parseDouble(m.group(1));
    }
}
