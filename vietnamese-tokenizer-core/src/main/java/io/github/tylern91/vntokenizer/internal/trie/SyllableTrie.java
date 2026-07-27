package io.github.tylern91.vntokenizer.internal.trie;

/**
 * Syllable-membership trie: terminal nodes mark valid Vietnamese syllables.
 * Unlike {@link WordTrie}, syllables need no identity — only membership.
 */
public final class SyllableTrie extends DoubleArrayTrie {

    private final boolean[] terminal;

    public SyllableTrie(int[] charMap, int[] base, int[] parent, boolean[] terminal) {
        super(charMap, base, parent);
        this.terminal = terminal;
    }

    public boolean isSyllableEnd(int node) {
        return terminal[node];
    }

    /**
     * Checks whether {@code codepoints[start, end)} is a valid syllable.
     */
    public boolean containsSyllable(int[] codepoints, int start, int end) {
        int node = 0;
        for (int i = start; i < end; i++) {
            node = findChild(node, codepoints[i]);
            if (node == -1) {
                return false;
            }
        }
        return isSyllableEnd(node);
    }
}
