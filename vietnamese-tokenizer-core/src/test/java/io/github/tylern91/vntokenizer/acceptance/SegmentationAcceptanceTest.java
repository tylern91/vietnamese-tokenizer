package io.github.tylern91.vntokenizer.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tylern91.vntokenizer.Token;
import io.github.tylern91.vntokenizer.Token.Type;
import io.github.tylern91.vntokenizer.VnTokenizer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentationAcceptanceTest {

    private static List<String> texts(List<Token> tokens) {
        return tokens.stream().map(Token::text).toList();
    }

    private static List<Type> types(List<Token> tokens) {
        return tokens.stream().map(Token::type).toList();
    }

    /**
     * Word ids (sorted order): aa=0, "aa bb"=1, bb=2, "bb cc"=3, cc=4, zz=5. The large {@code zz}
     * count keeps every other word's unigram probability small, so the bigram bonus
     * {@code ln(count(aa,"bb cc") / count(aa)) - lnP("bb cc")} = {@code -lnP("bb cc")} is large.
     *
     * <p>Total = 100 + 200 + 1 + 100 + 200 + 100000 = 100601. lnP(100) = -6.9137, lnP(200) = -6.2206.
     * Both candidate paths cross exactly one whitespace edge (-50):
     * <ul>
     *   <li>[aa bb][ ][cc] = -6.2206 - 50 - 6.2206 = -62.4412 (wins without any bigram)</li>
     *   <li>[aa][ ][bb cc] = -6.9137 - 50 - 6.9137 = -63.8275, plus bigram bonus +6.9137 = -56.9137
     *       (wins by about 5.5 once the bigram across the space applies)</li>
     * </ul>
     */
    @Test
    @DisplayName("[AC-2] a bigram between consecutive dictionary words carries across the space between them")
    void bigramContextCarriesAcrossSpace(@TempDir Path dir) {
        VnTokenizer tokenizer = AcceptanceFixtures.tokenizerFor(dir,
                List.of("aa\t100", "aa bb\t200", "bb\t1", "bb cc\t100", "cc\t200", "zz\t100000"),
                List.of("aa", "bb", "cc", "zz"),
                List.of("aa\tbb cc\t100"));

        List<Token> tokens = tokenizer.tokenize("aa bb cc");

        assertEquals(List.of("aa", " ", "bb cc"), texts(tokens));
        assertEquals(List.of(Type.WORD, Type.SPACE, Type.WORD), types(tokens));
    }

    /**
     * One word "học sinh" (count == total, so unigram log-prob is ln(1) = 0). Paths over "thọc sinh":
     * <ul>
     *   <li>[t](-50) + [học sinh](0) = -50 (the mid-syllable path; wins on the current scoring)</li>
     *   <li>[thọc](-25) + [ ](-50) + [sinh](-25) = -100</li>
     * </ul>
     */
    @Test
    @DisplayName("[AC-3] a word never starts in the middle of a syllable")
    void wordDoesNotStartMidSyllable(@TempDir Path dir) {
        VnTokenizer tokenizer = AcceptanceFixtures.tokenizerFor(dir,
                List.of("học sinh\t10"),
                List.of("học", "sinh", "thọc"),
                List.of());

        List<Token> tokens = tokenizer.tokenize("thọc sinh");

        assertEquals(List.of("thọc", " ", "sinh"), texts(tokens));
        assertEquals(List.of(Type.WORD, Type.SPACE, Type.WORD), types(tokens));
    }
}
