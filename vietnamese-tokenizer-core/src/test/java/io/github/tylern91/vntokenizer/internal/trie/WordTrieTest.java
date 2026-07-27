package io.github.tylern91.vntokenizer.internal.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

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

    // ---- longestWordFrom -----------------------------------------------------

    @Test
    void longestWordFromReturnsDeepestMatch() {
        int[] codepoints = {'b', 'a', 'n'};
        // "ba" and "ban" both match starting at 0; the deepest (ban, id 2) wins.
        assertEquals(2, trie.longestWordFrom(codepoints, 0));
    }

    @Test
    void longestWordFromStopsAtLongestNonMatchingTail() {
        int[] codepoints = {'b', 'a', 'x'};
        // "ba" matches (id 0); 'x' has no mapping so the walk stops there.
        assertEquals(0, trie.longestWordFrom(codepoints, 0));
    }

    @Test
    void longestWordFromNoMatchReturnsMinusOne() {
        int[] codepoints = {'z'};
        assertEquals(-1, trie.longestWordFrom(codepoints, 0));
    }

    @Test
    void longestWordFromPrefixOnlyReturnsMinusOne() {
        int[] codepoints = {'b'};
        // "b" alone is a prefix node, never a word end.
        assertEquals(-1, trie.longestWordFrom(codepoints, 0));
    }

    @Test
    void longestWordFromRespectsStartOffset() {
        int[] codepoints = {'x', 'x', 'c', 'a'};
        assertEquals(1, trie.longestWordFrom(codepoints, 2));
    }

    @Test
    void longestWordFromAtEndOfArrayReturnsMinusOne() {
        int[] codepoints = {'b', 'a'};
        assertEquals(-1, trie.longestWordFrom(codepoints, codepoints.length));
    }

    // ---- matchesFrom -----------------------------------------------------

    @Test
    void matchesFromEmitsEveryMatchNotJustLongest() {
        int[] codepoints = {'b', 'a', 'n'};
        List<int[]> matches = new ArrayList<>();
        trie.matchesFrom(codepoints, 0, (endExclusive, wordId) -> matches.add(new int[] {endExclusive, wordId}));

        assertEquals(2, matches.size());
        assertArrayEquals(new int[] {2, 0}, matches.get(0)); // "ba" ends at 2, id 0
        assertArrayEquals(new int[] {3, 2}, matches.get(1)); // "ban" ends at 3, id 2
    }

    @Test
    void matchesFromNoMatchInvokesSinkZeroTimes() {
        int[] codepoints = {'z'};
        List<int[]> matches = new ArrayList<>();
        trie.matchesFrom(codepoints, 0, (endExclusive, wordId) -> matches.add(new int[] {endExclusive, wordId}));
        assertTrue(matches.isEmpty());
    }

    @Test
    void matchesFromStopsWalkOnDeadEndButKeepsPriorMatches() {
        int[] codepoints = {'c', 'a', 'x'};
        List<int[]> matches = new ArrayList<>();
        trie.matchesFrom(codepoints, 0, (endExclusive, wordId) -> matches.add(new int[] {endExclusive, wordId}));
        assertEquals(1, matches.size());
        assertArrayEquals(new int[] {2, 1}, matches.get(0)); // "ca" ends at 2, id 1
    }
}
