package io.github.tylern91.vntokenizer.internal.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Same fixture layout as {@link DoubleArrayTrieTest}: {"ba", "ban", "bat", "ca"}.
 * Terminal (syllable-end) nodes: 4=ba, 5=ca, 6=ban, 7=bat. Prefix-only nodes
 * (2=b, 3=c) are not terminal — syllables need no identity, only membership.
 */
class SyllableTrieTest {

    private SyllableTrie trie;

    @BeforeEach
    void setUp() {
        int[] charMap = new int[128];
        charMap['a'] = 1;
        charMap['b'] = 2;
        charMap['c'] = 3;
        charMap['n'] = 4;
        charMap['t'] = 5;

        int[] base     = {0, 0, 3, 4, 2, 0, 0, 0};
        int[] parent   = {-1, -1, 0, 0, 2, 3, 4, 4};
        boolean[] term = {false, false, false, false, true, true, true, true};

        trie = new SyllableTrie(charMap, base, parent, term);
    }

    @Test
    void prefixNodeIsNotSyllableEnd() {
        int b = trie.findChild(0, 'b');
        assertFalse(trie.isSyllableEnd(b));
    }

    @Test
    void baIsSyllableEnd() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        assertTrue(trie.isSyllableEnd(ba));
    }

    @Test
    void banIsSyllableEnd() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        int ban = trie.findChild(ba, 'n');
        assertTrue(trie.isSyllableEnd(ban));
    }

    @Test
    void inheritedFindChildStillWorks() {
        int c = trie.findChild(0, 'c');
        int ca = trie.findChild(c, 'a');
        assertEquals(5, ca);
        assertTrue(trie.isSyllableEnd(ca));
    }

    @Test
    void containsSyllableExactSpanIsTrue() {
        int[] codepoints = {'b', 'a', 'n'};
        assertTrue(trie.containsSyllable(codepoints, 0, 3));
    }

    @Test
    void containsSyllablePartialSpanIsFalseWhenNotTerminal() {
        int[] codepoints = {'b', 'a', 'n'};
        // span [0,1) is just "b" — a prefix node, not a syllable end.
        assertFalse(trie.containsSyllable(codepoints, 0, 1));
    }

    @Test
    void containsSyllableTerminalPrefixOfLongerWordIsTrue() {
        int[] codepoints = {'b', 'a', 'n'};
        // span [0,2) is "ba" — itself a terminal node, even though "ban" also exists.
        assertTrue(trie.containsSyllable(codepoints, 0, 2));
    }

    @Test
    void containsSyllableNoMatchIsFalse() {
        int[] codepoints = {'z', 'z'};
        assertFalse(trie.containsSyllable(codepoints, 0, 2));
    }

    @Test
    void containsSyllableDeadEndMidSpanIsFalse() {
        int[] codepoints = {'b', 'a', 'x'};
        assertFalse(trie.containsSyllable(codepoints, 0, 3));
    }

    @Test
    void containsSyllableRespectsStartOffset() {
        int[] codepoints = {'x', 'x', 'c', 'a'};
        assertTrue(trie.containsSyllable(codepoints, 2, 4));
    }
}
