package io.github.tylern91.vntokenizer.internal.trie;

public abstract class DoubleArrayTrie {

    protected final int[] charMap;
    protected final int[] base;
    protected final int[] parent;

    protected DoubleArrayTrie(int[] charMap, int[] base, int[] parent) {
        this.charMap = charMap;
        this.base = base;
        this.parent = parent;
    }

    /**
     * Returns the child node reached from {@code node} via {@code codepoint},
     * or -1 if no such edge exists.
     *
     * charMap index 0 is the unmapped sentinel; valid mapped indices start at 1.
     */
    public int findChild(int node, int codepoint) {
        if (codepoint < 0 || codepoint >= charMap.length) return -1;
        int mapped = charMap[codepoint];
        if (mapped == 0) return -1;
        int t = base[node] + mapped;
        if (t < 0 || t >= parent.length) return -1;
        if (parent[t] != node) return -1;
        return t;
    }

    public boolean isValidNode(int node) {
        return node >= 0 && node < base.length;
    }

    public int poolSize() {
        return base.length;
    }
}
