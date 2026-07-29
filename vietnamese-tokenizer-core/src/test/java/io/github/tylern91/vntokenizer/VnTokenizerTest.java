package io.github.tylern91.vntokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code PER_CLASS} + {@code @AfterAll} reset (rather than {@code @AfterEach}) so the bundled
 * singleton's real dictionary load (~30s against the full corpus, see Step 4) is paid once for
 * the whole class instead of once per test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VnTokenizerTest {

    private static void writeFixture(Path dir) throws IOException {
        Files.write(dir.resolve("words.txt"), List.of("ba\t100", "học\t30", "sinh\t20"), StandardCharsets.UTF_8);
        Files.write(dir.resolve("syllables.txt"), List.of("ba", "học", "sinh"), StandardCharsets.UTF_8);
        Files.write(dir.resolve("bigrams.txt"), List.of("học\tsinh\t10"), StandardCharsets.UTF_8);
    }

    @AfterAll
    void resetCache() {
        VnTokenizer.resetInstances();
    }

    @Test
    void bundledSingletonIdentity() {
        VnTokenizer a = VnTokenizer.getInstance();
        VnTokenizer b = VnTokenizer.getInstance();
        assertSame(a, b);
    }

    @Test
    void customPathSingletonIdentity(@TempDir Path dir) throws IOException {
        writeFixture(dir);
        VnTokenizer a = VnTokenizer.getInstance(dir);
        VnTokenizer b = VnTokenizer.getInstance(dir);
        assertSame(a, b);
    }

    @Test
    void differentPathsProduceDifferentInstances(@TempDir Path dir) throws IOException {
        writeFixture(dir);
        VnTokenizer bundled = VnTokenizer.getInstance();
        VnTokenizer custom = VnTokenizer.getInstance(dir);
        assertNotSame(bundled, custom);
    }

    @Test
    void tokenizeProducesNonEmptyTokensCoveringTheWholeInput() {
        VnTokenizer t = VnTokenizer.getInstance();
        String text = "Xin chào Việt Nam";

        List<Token> tokens = t.tokenize(text);

        assertFalse(tokens.isEmpty());
        StringBuilder rebuilt = new StringBuilder();
        for (Token token : tokens) {
            rebuilt.append(token.text());
        }
        assertEquals(Normalizer.normalize(text, Normalizer.Form.NFC), rebuilt.toString());
        assertTrue(tokens.stream().anyMatch(token -> token.type() == Token.Type.WORD));
    }

    @Test
    void tokenizeWithExplicitNormalOptionMatchesDefault() {
        VnTokenizer t = VnTokenizer.getInstance();
        String text = "Xin chào Việt Nam";

        assertEquals(t.tokenize(text), t.tokenize(text, TokenizeOption.NORMAL));
    }

    @Test
    void tokenizeToStringsReturnsTokenTexts() {
        VnTokenizer t = VnTokenizer.getInstance();
        String text = "Xin chào Việt Nam";

        List<Token> tokens = t.tokenize(text);
        List<String> texts = t.tokenizeToStrings(text);

        assertEquals(tokens.stream().map(Token::text).toList(), texts);
    }

    @Test
    void oovNumberRunMergesIntoOneNumberToken(@TempDir Path dir) throws IOException {
        writeFixture(dir);
        VnTokenizer t = VnTokenizer.getInstance(dir);

        List<Token> tokens = t.tokenize("ba 123 sinh");

        assertTrue(tokens.stream().anyMatch(token -> token.type() == Token.Type.NUMBER && token.text().equals("123")));
    }

    @Test
    void oovPunctuationClassifiedAsPunct(@TempDir Path dir) throws IOException {
        writeFixture(dir);
        VnTokenizer t = VnTokenizer.getInstance(dir);

        List<Token> tokens = t.tokenize("ba!");

        assertTrue(tokens.stream().anyMatch(token -> token.type() == Token.Type.PUNCT && token.text().equals("!")));
    }

    @Test
    void oovWhitespaceClassifiedAsSpace(@TempDir Path dir) throws IOException {
        writeFixture(dir);
        VnTokenizer t = VnTokenizer.getInstance(dir);

        List<Token> tokens = t.tokenize("ba 123");

        assertTrue(tokens.stream().anyMatch(token -> token.type() == Token.Type.SPACE));
    }

    @Test
    void hostModeKeepsHostnameIntact() {
        VnTokenizer t = VnTokenizer.getInstance();

        List<Token> tokens = t.tokenize("Xin chào example.com hôm nay", TokenizeOption.HOST);

        assertTrue(tokens.stream().anyMatch(token -> token.text().equals("example.com")));
    }

    @Test
    void urlModeKeepsFullUrlIntact() {
        VnTokenizer t = VnTokenizer.getInstance();

        List<Token> tokens = t.tokenize("xem tại https://example.com/path hôm nay", TokenizeOption.URL);

        assertTrue(tokens.stream().anyMatch(token -> token.text().equals("https://example.com/path")));
    }

    @Test
    void urlModeHandlesSupplementaryPlaneCodepointBeforeAtomWithoutCorruption() {
        VnTokenizer t = VnTokenizer.getInstance();
        // U+1F600 (😀) is a supplementary-plane codepoint: 1 codepoint but 2 UTF-16 chars,
        // so a char-offset/codepoint-offset mixup surfaces as soon as it precedes a URL match.
        String text = "😀https://example.com";

        List<Token> tokens = t.tokenize(text, TokenizeOption.URL);

        StringBuilder rebuilt = new StringBuilder();
        for (Token token : tokens) {
            rebuilt.append(token.text());
        }
        assertEquals(text, rebuilt.toString());
    }

    @Test
    void hostModeDoesNotStackOverflowOnManyDottedLabels() {
        VnTokenizer t = VnTokenizer.getInstance();
        StringBuilder manyLabels = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            manyLabels.append("a.");
        }
        manyLabels.append("com");

        assertDoesNotThrow(() -> t.tokenize(manyLabels.toString(), TokenizeOption.HOST));
    }
}
