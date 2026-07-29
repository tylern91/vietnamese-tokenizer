package io.github.tylern91.vntokenizer;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Corpus-backed end-to-end checks against the real bundled dictionary — as opposed
 * to {@link VnTokenizerTest}'s structural assertions against a controlled fixture.
 */
class VnTokenizerCorpusIntegrationTest {

    @Test
    void haNoiSegmentsAsOneWord() {
        List<Token> tokens = VnTokenizer.getInstance().tokenize("Hà Nội");
        assertEquals(List.of(new Token("Hà Nội", Token.Type.WORD, 0, 6)), tokens);
    }

    @Test
    void hocSinhSegmentsAsOneWord() {
        List<Token> tokens = VnTokenizer.getInstance().tokenize("học sinh");
        assertEquals(List.of(new Token("học sinh", Token.Type.WORD, 0, 8)), tokens);
    }

    @Test
    void vietNamSegmentsAsOneWord() {
        List<Token> tokens = VnTokenizer.getInstance().tokenize("Việt Nam");
        assertEquals(List.of(new Token("Việt Nam", Token.Type.WORD, 0, 8)), tokens);
    }

    @Test
    void urlModeKeepsUrlIntactAndSegmentsSurroundingText() {
        List<Token> tokens =
                VnTokenizer.getInstance().tokenize("xem tại https://example.com/path hôm nay", TokenizeOption.URL);

        assertTrue(tokens.stream()
                .anyMatch(t -> t.text().equals("https://example.com/path") && t.type() == Token.Type.WORD));
    }
}
