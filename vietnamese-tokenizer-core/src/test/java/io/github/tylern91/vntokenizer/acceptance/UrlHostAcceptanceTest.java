package io.github.tylern91.vntokenizer.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tylern91.vntokenizer.Token;
import io.github.tylern91.vntokenizer.Token.Type;
import io.github.tylern91.vntokenizer.TokenizeOption;
import io.github.tylern91.vntokenizer.VnTokenizer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UrlHostAcceptanceTest {

    @Test
    @DisplayName("[AC-6] URL mode keeps the URL as one WORD and splits trailing punctuation off as PUNCT")
    void urlModeExcludesTrailingPunctuation(@TempDir Path dir) {
        VnTokenizer tokenizer = AcceptanceFixtures.tokenizerFor(dir, List.of("xem\t10"), List.of("xem"), List.of());

        assertEquals(
                List.of(new Token("xem", Type.WORD, 0, 3),
                        new Token(" ", Type.SPACE, 3, 4),
                        new Token("https://a.vn/x", Type.WORD, 4, 18),
                        new Token(".", Type.PUNCT, 18, 19)),
                tokenizer.tokenize("xem https://a.vn/x.", TokenizeOption.URL));

        assertEquals(
                List.of(new Token("(", Type.PUNCT, 0, 1),
                        new Token("www.vnexpress.net", Type.WORD, 1, 18),
                        new Token(")", Type.PUNCT, 18, 19)),
                tokenizer.tokenize("(www.vnexpress.net)", TokenizeOption.URL));

        assertEquals(
                List.of(new Token("https://a.vn", Type.WORD, 0, 12),
                        new Token(",", Type.PUNCT, 12, 13)),
                tokenizer.tokenize("https://a.vn,", TokenizeOption.URL));
    }
}
