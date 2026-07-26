package io.github.tylern91.vntokenizer.internal.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Manual trie fixture for {"ba", "ban", "bat", "ca"}.
 *
 * charMap: 'a'->1  'b'->2  'c'->3  'n'->4  't'->5
 * Node 0 root: base=0
 * Node 2 (b):  base=3, parent=0   [base[0]+charMap['b']=0+2=2]
 * Node 3 (c):  base=4, parent=0   [base[0]+charMap['c']=0+3=3]
 * Node 4 (ba): base=2, parent=2   [base[2]+charMap['a']=3+1=4]
 * Node 5 (ca): base=0, parent=3   [base[3]+charMap['a']=4+1=5]
 * Node 6 (ban):base=0, parent=4   [base[4]+charMap['n']=2+4=6]
 * Node 7 (bat):base=0, parent=4   [base[4]+charMap['t']=2+5=7]
 */
class DoubleArrayTrieTest {

    private DoubleArrayTrie trie;
    private DoubleArrayTrie emptyTrie;

    private static final class TestTrie extends DoubleArrayTrie {
        TestTrie(int[] charMap, int[] base, int[] parent) {
            super(charMap, base, parent);
        }
    }

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

        trie = new TestTrie(charMap, base, parent);

        emptyTrie = new TestTrie(new int[1], new int[]{0}, new int[]{-1});
    }

    @Test
    void rootToB() {
        assertEquals(2, trie.findChild(0, 'b'));
    }

    @Test
    void rootToC() {
        assertEquals(3, trie.findChild(0, 'c'));
    }

    @Test
    void bToA_reachesBa() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        assertEquals(4, ba);
    }

    @Test
    void cToA_reachesCa() {
        int c = trie.findChild(0, 'c');
        int ca = trie.findChild(c, 'a');
        assertEquals(5, ca);
    }

    @Test
    void baToN_reachesBan() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        int ban = trie.findChild(ba, 'n');
        assertEquals(6, ban);
    }

    @Test
    void baToT_reachesBat() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        int bat = trie.findChild(ba, 't');
        assertEquals(7, bat);
    }

    @Test
    void unknownCodepointReturnsMinusOne() {
        assertEquals(-1, trie.findChild(0, 'z'));
    }

    @Test
    void wrongPathReturnsMinusOne() {
        // 'n' is not a child of root
        assertEquals(-1, trie.findChild(0, 'n'));
    }

    @Test
    void leafNodeHasNoChildren() {
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        int ban = trie.findChild(ba, 'n');
        // ban is a leaf — no children
        assertEquals(-1, trie.findChild(ban, 'a'));
    }

    @Test
    void negativeCodepointReturnsMinusOne() {
        assertEquals(-1, trie.findChild(0, -1));
    }

    @Test
    void hugeCodepointReturnsMinusOne() {
        assertEquals(-1, trie.findChild(0, Integer.MAX_VALUE));
    }

    @Test
    void emptyTrieAllChildrenMinusOne() {
        assertEquals(-1, emptyTrie.findChild(0, 'a'));
        assertEquals(-1, emptyTrie.findChild(0, 'b'));
    }

    @Test
    void rootIsAlwaysValid() {
        assertTrue(trie.isValidNode(0));
    }

    @Test
    void knownNodesAreValid() {
        assertTrue(trie.isValidNode(2));
        assertTrue(trie.isValidNode(7));
    }

    @Test
    void negativeNodeIsInvalid() {
        assertFalse(trie.isValidNode(-1));
    }

    @Test
    void outOfBoundsNodeIsInvalid() {
        assertFalse(trie.isValidNode(trie.poolSize()));
    }

    @Test
    void poolSize() {
        assertEquals(8, trie.poolSize());
    }

    @Test
    void vietnameseCodepointInCharMap() {
        // Build a mini-trie that maps a Vietnamese codepoint (e.g., 'ê' U+00EA = 234)
        int[] vCharMap = new int[300];
        vCharMap[0xEA] = 1; // 'ê'
        int[] vBase   = {0, 0};
        int[] vParent = {-1, 0};
        DoubleArrayTrie vTrie = new TestTrie(vCharMap, vBase, vParent);
        assertEquals(1, vTrie.findChild(0, 0xEA));
        assertEquals(-1, vTrie.findChild(0, 0xEB));
    }
}
