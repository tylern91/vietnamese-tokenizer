package io.github.tylern91.vntokenizer.internal.dict;

import io.github.tylern91.vntokenizer.internal.score.BigramScores;
import io.github.tylern91.vntokenizer.internal.trie.SyllableTrie;
import io.github.tylern91.vntokenizer.internal.trie.WordTrie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DictLoaderTest {

    private static final List<String> WORDS = List.of(
            "ba\t100",
            "hà nội\t50",
            "học\t30",
            "sinh\t20");
    private static final List<String> SYLLABLES = List.of("ba", "hà", "học", "nội", "sinh");
    private static final List<String> BIGRAMS = List.of(
            "học\tsinh\t10",
            "hà nội\tba\t5",
            "hà nội\tunknown word\t3");

    private static Path writeFixture(Path dir, List<String> words, List<String> syllables, List<String> bigrams)
            throws IOException {
        Files.write(dir.resolve("words.txt"), words, StandardCharsets.UTF_8);
        Files.write(dir.resolve("syllables.txt"), syllables, StandardCharsets.UTF_8);
        Files.write(dir.resolve("bigrams.txt"), bigrams, StandardCharsets.UTF_8);
        return dir;
    }

    private static int wordIdOf(WordTrie trie, String word) {
        int[] cps = word.codePoints().toArray();
        return trie.longestWordFrom(cps, 0);
    }

    @Test
    void loadFromPathBuildsWordTrieContainingEveryWord(@TempDir Path dir) throws IOException {
        writeFixture(dir, WORDS, SYLLABLES, BIGRAMS);
        Dictionaries dict = DictLoader.load(dir);

        assertNotEquals(-1, wordIdOf(dict.wordTrie(), "ba"));
        assertNotEquals(-1, wordIdOf(dict.wordTrie(), "hà nội"));
        assertNotEquals(-1, wordIdOf(dict.wordTrie(), "học"));
        assertNotEquals(-1, wordIdOf(dict.wordTrie(), "sinh"));
    }

    @Test
    void loadFromPathBuildsSyllableTrieContainingEverySyllable(@TempDir Path dir) throws IOException {
        writeFixture(dir, WORDS, SYLLABLES, BIGRAMS);
        Dictionaries dict = DictLoader.load(dir);

        SyllableTrie trie = dict.syllableTrie();
        for (String syllable : SYLLABLES) {
            int[] cps = syllable.codePoints().toArray();
            assertTrue(trie.containsSyllable(cps, 0, cps.length), () -> "missing syllable: " + syllable);
        }
    }

    @Test
    void unigramLogProbIsNegativeAndOrdersByFrequency(@TempDir Path dir) throws IOException {
        writeFixture(dir, WORDS, SYLLABLES, BIGRAMS);
        Dictionaries dict = DictLoader.load(dir);

        int ba = wordIdOf(dict.wordTrie(), "ba");
        int sinh = wordIdOf(dict.wordTrie(), "sinh");

        // "ba" (count=100) is more frequent than "sinh" (count=20), so its
        // log-probability must be less negative (closer to 0).
        assertTrue(dict.unigramLogProb(ba) > dict.unigramLogProb(sinh));
        assertTrue(dict.unigramLogProb(ba) < 0.0f);
        assertTrue(dict.unigramLogProb(sinh) < 0.0f);
    }

    @Test
    void bigramScoreMatchesPmiFormulaForKnownPair(@TempDir Path dir) throws IOException {
        writeFixture(dir, WORDS, SYLLABLES, BIGRAMS);
        Dictionaries dict = DictLoader.load(dir);

        int hocId = wordIdOf(dict.wordTrie(), "học");
        int sinhId = wordIdOf(dict.wordTrie(), "sinh");

        // pmi = log(bigramCount / prevWordCount) - unigramLogProb[nextId]
        //     = log(10 / 30) - log(20 / total)
        double total = 100 + 50 + 30 + 20;
        double expected = Math.log(10.0 / 30.0) - Math.log(20.0 / total);
        assertEquals((float) expected, dict.bigramScores().score(hocId, sinhId), 1e-5f);
    }

    @Test
    void bigramReferencingUnknownWordIsSkippedNotThrown(@TempDir Path dir) throws IOException {
        writeFixture(dir, WORDS, SYLLABLES, BIGRAMS);
        // "hà nội\tunknown word\t3" references a word absent from words.txt --
        // must be silently skipped, not fail the whole load.
        assertDoesNotThrow(() -> DictLoader.load(dir));

        Dictionaries dict = DictLoader.load(dir);
        int hanoi = wordIdOf(dict.wordTrie(), "hà nội");
        // No entry was recorded for the OOV pair, so score() falls back to the default.
        assertEquals(BigramScores.DEFAULT_SCORE, dict.bigramScores().score(hanoi, 999));
    }

    @Test
    void missingWordsFileFailsLoud(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("syllables.txt"), SYLLABLES, StandardCharsets.UTF_8);
        Files.write(dir.resolve("bigrams.txt"), BIGRAMS, StandardCharsets.UTF_8);
        assertThrows(UncheckedIOException.class, () -> DictLoader.load(dir));
    }

    @Test
    void malformedWordLineFailsLoud(@TempDir Path dir) throws IOException {
        writeFixture(dir, List.of("no-tab-or-count"), SYLLABLES, BIGRAMS);
        assertThrows(IllegalStateException.class, () -> DictLoader.load(dir));
    }

    @Test
    void bundledLoadDoesNotThrowAndProducesNonEmptyDictionaries() {
        Dictionaries dict = DictLoader.load();

        assertNotEquals(-1, wordIdOf(dict.wordTrie(), "học sinh"));
        int[] haCps = "hà".codePoints().toArray();
        assertTrue(dict.syllableTrie().containsSyllable(haCps, 0, haCps.length));
    }
}
