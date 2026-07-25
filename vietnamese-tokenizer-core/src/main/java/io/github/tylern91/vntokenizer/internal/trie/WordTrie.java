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
     */
    public int longestWordFrom(int[] codepoints, int start) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 4");
    }
}
