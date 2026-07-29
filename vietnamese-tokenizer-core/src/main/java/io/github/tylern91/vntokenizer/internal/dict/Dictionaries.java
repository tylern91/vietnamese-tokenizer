package io.github.tylern91.vntokenizer.internal.dict;

import io.github.tylern91.vntokenizer.internal.score.BigramScores;
import io.github.tylern91.vntokenizer.internal.trie.SyllableTrie;
import io.github.tylern91.vntokenizer.internal.trie.WordTrie;

/**
 * Holds the loaded dictionary artifacts a {@code DictLoader} builds: the word
 * and syllable tries, per-word unigram log-probabilities, and bigram scores.
 */
public final class Dictionaries {

    private final WordTrie wordTrie;
    private final SyllableTrie syllableTrie;
    private final float[] unigramLogProb;
    private final BigramScores bigramScores;

    public Dictionaries(WordTrie wordTrie, SyllableTrie syllableTrie, float[] unigramLogProb,
            BigramScores bigramScores) {
        this.wordTrie = wordTrie;
        this.syllableTrie = syllableTrie;
        this.unigramLogProb = unigramLogProb;
        this.bigramScores = bigramScores;
    }

    public WordTrie wordTrie() {
        return wordTrie;
    }

    public SyllableTrie syllableTrie() {
        return syllableTrie;
    }

    public float unigramLogProb(int wordId) {
        return unigramLogProb[wordId];
    }

    public BigramScores bigramScores() {
        return bigramScores;
    }
}
