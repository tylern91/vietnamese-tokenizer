package io.github.tylern91.vntokenizer.internal.decode;

import io.github.tylern91.vntokenizer.internal.dict.Dictionaries;
import io.github.tylern91.vntokenizer.internal.score.BigramScores;
import io.github.tylern91.vntokenizer.internal.trie.DoubleArrayTrieBuilder;
import io.github.tylern91.vntokenizer.internal.trie.SyllableTrie;
import io.github.tylern91.vntokenizer.internal.trie.WordTrie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixture words are sorted lexicographically per {@link DoubleArrayTrieBuilder}'s
 * contract; wordId is each word's index in that sorted list.
 */
class ViterbiSegmenterTest {

    private static int[] cp(String s) {
        return s.codePoints().toArray();
    }

    @Test
    void bigramScorePrefersSplitOverWholeWordMatch() {
        List<String> words = List.of("ab", "abc", "c"); // ab=0, abc=1, c=2
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(words);
        float[] unigramLogProb = {-1.0f, -1.0f, -1.0f}; // equal unigram cost for all three
        Map<Long, Float> bigrams = Map.of(BigramScores.key(0, 2), 5.0f); // "ab" -> "c" strongly favored
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.of(bigrams));

        List<ViterbiSegmenter.Span> spans = new ViterbiSegmenter(dictionaries).segment(cp("abc"));

        assertEquals(List.of(new ViterbiSegmenter.Span(0, 2, 0), new ViterbiSegmenter.Span(2, 3, 2)), spans);
    }

    @Test
    void wholeWordWinsWhenNoBigramBoostsTheSplit() {
        List<String> words = List.of("ab", "abc", "c"); // ab=0, abc=1, c=2
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(words);
        float[] unigramLogProb = {-1.0f, -0.1f, -1.0f}; // "abc" far more likely as a whole word
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.empty());

        List<ViterbiSegmenter.Span> spans = new ViterbiSegmenter(dictionaries).segment(cp("abc"));

        assertEquals(List.of(new ViterbiSegmenter.Span(0, 3, 1)), spans);
    }

    @Test
    void gracefulDegradationWithEmptyBigramMapActsLikeLongestMatch() {
        List<String> words = List.of("a", "ab", "abc"); // a=0, ab=1, abc=2
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(words);
        // Flat unigram cost: with no bigram signal at all, the DP has nothing to prefer
        // a shorter split over the single longest match that covers the whole input.
        float[] unigramLogProb = {-1.0f, -1.0f, -1.0f};
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.empty());

        List<ViterbiSegmenter.Span> spans = new ViterbiSegmenter(dictionaries).segment(cp("abc"));

        assertEquals(List.of(new ViterbiSegmenter.Span(0, 3, 2)), spans);
    }

    @Test
    void recognizedSyllableWithNoWordMatchProducesOneFallbackSpan() {
        List<String> words = List.of("ab");
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(List.of("xy")); // recognized syllable, not a dictionary word
        float[] unigramLogProb = {-1.0f};
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.empty());

        List<ViterbiSegmenter.Span> spans = new ViterbiSegmenter(dictionaries).segment(cp("xy"));

        assertEquals(List.of(new ViterbiSegmenter.Span(0, 2, ViterbiSegmenter.OOV_WORD_ID)), spans);
    }

    @Test
    void unrecognizedRunFallsBackToSingleCodepointSpans() {
        List<String> words = List.of("ab");
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(List.of("ab")); // "zq" not recognized
        float[] unigramLogProb = {-1.0f};
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.empty());

        List<ViterbiSegmenter.Span> spans = new ViterbiSegmenter(dictionaries).segment(cp("zq"));

        assertEquals(List.of(
                new ViterbiSegmenter.Span(0, 1, ViterbiSegmenter.OOV_WORD_ID),
                new ViterbiSegmenter.Span(1, 2, ViterbiSegmenter.OOV_WORD_ID)), spans);
    }

    @Test
    void emptyInputProducesNoSpans() {
        List<String> words = List.of("ab");
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(words);
        float[] unigramLogProb = {-1.0f};
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.empty());

        List<ViterbiSegmenter.Span> spans = new ViterbiSegmenter(dictionaries).segment(new int[0]);

        assertTrue(spans.isEmpty());
    }

    @Test
    void segmentingLongWhitespaceFreeOovRunCompletesQuickly() {
        List<String> words = List.of("ab");
        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(words);
        float[] unigramLogProb = {-1.0f};
        Dictionaries dictionaries = new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.empty());
        int[] longOovRun = cp("z".repeat(200_000)); // no whitespace, no word/syllable match anywhere

        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> new ViterbiSegmenter(dictionaries).segment(longOovRun));
    }
}
