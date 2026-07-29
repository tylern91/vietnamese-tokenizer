package io.github.tylern91.vntokenizer.internal.trie;

/**
 * Word-lookup trie: each terminal node carries a word identity for use by
 * later bigram scoring (Phase 4).
 */
public final class WordTrie extends DoubleArrayTrie {

    private final int[] wordId;

    /**
     * @param wordId parallel to the node pool; -1 = not a word end, otherwise
     *               the id of the word terminating at that node.
     */
    public WordTrie(int[] charMap, int[] base, int[] parent, int[] wordId) {
        super(charMap, base, parent);
        this.wordId = wordId;
    }

    public boolean isWordEnd(int node) {
        return wordId[node] >= 0;
    }

    public int wordIdAt(int node) {
        return wordId[node];
    }

    /**
     * Finds the longest word matching a prefix of {@code codepoints} starting
     * at {@code start}, returning its word id (or -1 if none matches).
     *
     * <p>Matching folds case since dictionary entries are stored lowercase but real
     * Vietnamese text capitalizes proper nouns and sentence-initial letters.
     */
    public int longestWordFrom(int[] codepoints, int start) {
        int node = 0;
        int bestWordId = -1;
        for (int i = start; i < codepoints.length; i++) {
            node = findChild(node, Character.toLowerCase(codepoints[i]));
            if (node == -1) {
                break;
            }
            if (isWordEnd(node)) {
                bestWordId = wordIdAt(node);
            }
        }
        return bestWordId;
    }

    /**
     * Emits every word matching a prefix of {@code codepoints} starting at
     * {@code start} — not just the longest — for lattice construction. Each
     * match reports the exclusive end position and the matched word's id.
     */
    public void matchesFrom(int[] codepoints, int start, MatchSink sink) {
        int node = 0;
        for (int i = start; i < codepoints.length; i++) {
            node = findChild(node, Character.toLowerCase(codepoints[i]));
            if (node == -1) {
                break;
            }
            if (isWordEnd(node)) {
                sink.onMatch(i + 1, wordIdAt(node));
            }
        }
    }

    /** Receives one {@code (endExclusive, wordId)} pair per match found by {@link #matchesFrom}. */
    @FunctionalInterface
    public interface MatchSink {
        void onMatch(int endExclusive, int wordId);
    }
}
