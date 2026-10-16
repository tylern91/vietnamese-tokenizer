package io.github.tylern91.vntokenizer;

import io.github.tylern91.vntokenizer.internal.decode.ViterbiSegmenter;
import io.github.tylern91.vntokenizer.internal.dict.DictLoader;
import io.github.tylern91.vntokenizer.internal.dict.Dictionaries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code PER_CLASS} + {@code @AfterAll} reset (rather than {@code @AfterEach}) so the bundled
 * singleton's real dictionary load (~1s against the full corpus) is paid once for
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
    void penaltiesPassedToTheConstructorReachTheSegmenter(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("words.txt"), List.of("học\t30", "học sinh\t5", "sinh\t20"), StandardCharsets.UTF_8);
        Files.write(dir.resolve("syllables.txt"), List.of("học", "sinh"), StandardCharsets.UTF_8);
        Files.write(dir.resolve("bigrams.txt"), List.of(), StandardCharsets.UTF_8);
        Dictionaries dictionaries = DictLoader.load(dir);

        VnTokenizer defaults = new VnTokenizer(dictionaries, ViterbiSegmenter.Penalties.DEFAULT);
        VnTokenizer freeSpaces = new VnTokenizer(dictionaries, new ViterbiSegmenter.Penalties(0.0, -25.0, -50.0, 1.0));

        assertEquals(List.of("học sinh"), defaults.tokenizeToStrings("học sinh"));
        assertEquals(List.of("học", " ", "sinh"), freeSpaces.tokenizeToStrings("học sinh"));
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

    @Test
    void concurrentTokenizationMatchesSequentialResults() throws Exception {
        VnTokenizer t = VnTokenizer.getInstance();
        List<String> inputs = List.of(
                "Xin chào Việt Nam",
                "Tôi là học sinh, bạn là sinh viên.",
                "xem https://a.vn/x. rồi gọi 0912345678",
                "Hà Nội 😀 mùa thu",
                "");
        List<List<Token>> expected = new ArrayList<>();
        for (String input : inputs) {
            expected.add(t.tokenize(input, TokenizeOption.URL));
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<List<Token>>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    List<List<Token>> results = new ArrayList<>();
                    for (int round = 0; round < 50; round++) {
                        results.clear();
                        for (String input : inputs) {
                            results.add(t.tokenize(input, TokenizeOption.URL));
                        }
                    }
                    return results;
                }));
            }
            for (Future<List<List<Token>>> future : futures) {
                assertEquals(expected, future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
