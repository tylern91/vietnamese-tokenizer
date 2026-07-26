package io.github.tylern91.vntokenizer.internal.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Same fixture layout as {@link DoubleArrayTrieTest}: {"ba", "ban", "bat", "ca"}.
 * Word-end nodes (4=ba, 5=ca, 6=ban, 7=bat) each carry a word id; all other
 * nodes (prefix-only, e.g. 2=b, 3=c) are non-word-ends (wordId = -1).
 */
class WordTrieTest {

    private WordTrie trie;

    @BeforeEach
    void setUp() {
        int[] charMap = new int[128];
        charMap['a'] = 1;
        charMap['b'] = 2;
        charMap['c'] = 3;
        charMap['n'] = 4;
        charMap['t'] = 5;

        int[] base   = {0, 0, 3, 4, 2, 0, 0, 0};
        int[] parent = {-1, -1, 0, 0, 2, 3, 4, 4};
        int[] wordId = {-1, -1, -1, -1, 0, 1, 2, 3}; // ba=0, ca=1, ban=2, bat=3

        trie = new WordTrie(charMap, base, parent, wordId);
    }

    @Test
    void prefixNodeIsNotWordEnd() {
        int b = trie.findChild(0, 'b');
        assertFalse(trie.isWordEnd(b));
    }

    @Test
    void baIsWordEnd() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        assertTrue(trie.isWordEnd(ba));
    }

    @Test
    void banIsWordEnd() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        int ban = trie.findChild(ba, 'n');
        assertTrue(trie.isWordEnd(ban));
    }

    @Test
    void wordIdAtReturnsCorrectId() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        int ban = trie.findChild(ba, 'n');
        int bat = trie.findChild(ba, 't');
        assertEquals(0, trie.wordIdAt(ba));
        assertEquals(2, trie.wordIdAt(ban));
        assertEquals(3, trie.wordIdAt(bat));
    }

    @Test
    void wordIdAtNonWordEndReturnsMinusOne() {
        int b = trie.findChild(0, 'b');
        assertEquals(-1, trie.wordIdAt(b));
    }

    @Test
    void inheritedFindChildStillWorks() {
        int c = trie.findChild(0, 'c');
        int ca = trie.findChild(c, 'a');
        assertEquals(5, ca);
        assertTrue(trie.isWordEnd(ca));
        assertEquals(1, trie.wordIdAt(ca));
    }

    @Test
    void longestWordFromThrowsUnsupported() {
        int[] codepoints = {'b', 'a', 'n'};
        assertThrows(UnsupportedOperationException.class,
                () -> trie.longestWordFrom(codepoints, 0));
    }
}
