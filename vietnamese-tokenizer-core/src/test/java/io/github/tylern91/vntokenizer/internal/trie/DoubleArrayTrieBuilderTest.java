package io.github.tylern91.vntokenizer.internal.trie;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral (round-trip) tests for {@link DoubleArrayTrieBuilder}. None of
 * these tests assert on internal {@code base}/{@code parent} array values —
 * those are implementation details of the builder, not part of the
 * {@link DoubleArrayTrie} contract. Every assertion goes through the public
 * {@code findChild}/{@code isWordEnd}/{@code wordIdAt}/{@code isSyllableEnd}
 * surface, exactly as a real caller would.
 */
class DoubleArrayTrieBuilderTest {

    // ---- 1. Empty input ----------------------------------------------------

    @Test
    void emptyWordListBuildsRootOnlyTrie() {
        WordTrie trie = DoubleArrayTrieBuilder.buildWordTrie(new String[0]);
        assertEquals(1, trie.poolSize());
        assertEquals(-1, trie.findChild(0, 'a'));
        assertEquals(-1, trie.findChild(0, 'z'));
        assertFalse(trie.isWordEnd(0));
    }

    @Test
    void emptySyllableListBuildsRootOnlyTrie() {
        SyllableTrie trie = DoubleArrayTrieBuilder.buildSyllableTrie(new String[0]);
        assertEquals(1, trie.poolSize());
        assertEquals(-1, trie.findChild(0, 'a'));
        assertFalse(trie.isSyllableEnd(0));
    }

    // ---- 2. Single word / syllable ------------------------------------------

    @Test
    void singleWordIsReachableAndIsWordEnd() {
        WordTrie trie = DoubleArrayTrieBuilder.buildWordTrie(new String[] {"ba"});

        int b = trie.findChild(0, 'b');
        assertNotEquals(-1, b);
        assertFalse(trie.isWordEnd(b));

        int ba = trie.findChild(b, 'a');
        assertNotEquals(-1, ba);
        assertTrue(trie.isWordEnd(ba));
        assertEquals(0, trie.wordIdAt(ba));

        // 'x' never appears in the input, so it must have no mapping at all.
        assertEquals(-1, trie.findChild(0, 'x'));
    }

    @Test
    void singleWordViaListOverload() {
        WordTrie trie = DoubleArrayTrieBuilder.buildWordTrie(List.of("ba"));
        int b = trie.findChild(0, 'b');
        int ba = trie.findChild(b, 'a');
        assertTrue(trie.isWordEnd(ba));
        assertEquals(0, trie.wordIdAt(ba));
    }

    @Test
    void singleSyllableIsReachableAndIsSyllableEnd() {
        SyllableTrie trie = DoubleArrayTrieBuilder.buildSyllableTrie(new String[] {"ba"});
        int b = trie.findChild(0, 'b');
        assertFalse(trie.isSyllableEnd(b));
        int ba = trie.findChild(b, 'a');
        assertTrue(trie.isSyllableEnd(ba));
    }

    // ---- 3. Shared prefixes: reuse {"ba","ban","bat","ca"} fixture ----------

    @Test
    void sharedPrefixWordsAreReachableWithCorrectIds() {
        // Same fixture as DoubleArrayTrieTest/WordTrieTest, but built (not
        // hand-assembled) — indices are the words' positions in this sorted array.
        String[] words = {"ba", "ban", "bat", "ca"};
        WordTrie trie = DoubleArrayTrieBuilder.buildWordTrie(words);

        int b = trie.findChild(0, 'b');
        assertFalse(trie.isWordEnd(b));

        int ba = trie.findChild(b, 'a');
        assertTrue(trie.isWordEnd(ba));
        assertEquals(0, trie.wordIdAt(ba));

        int ban = trie.findChild(ba, 'n');
        assertTrue(trie.isWordEnd(ban));
        assertEquals(1, trie.wordIdAt(ban));

        int bat = trie.findChild(ba, 't');
        assertTrue(trie.isWordEnd(bat));
        assertEquals(2, trie.wordIdAt(bat));

        int c = trie.findChild(0, 'c');
        assertFalse(trie.isWordEnd(c));

        int ca = trie.findChild(c, 'a');
        assertTrue(trie.isWordEnd(ca));
        assertEquals(3, trie.wordIdAt(ca));
    }

    @Test
    void sharedPrefixSyllablesAreReachable() {
        String[] syllables = {"ba", "ban", "bat", "ca"};
        SyllableTrie trie = DoubleArrayTrieBuilder.buildSyllableTrie(syllables);

        int b = trie.findChild(0, 'b');
        assertFalse(trie.isSyllableEnd(b));
        int ba = trie.findChild(b, 'a');
        assertTrue(trie.isSyllableEnd(ba));
        int ban = trie.findChild(ba, 'n');
        assertTrue(trie.isSyllableEnd(ban));
        int bat = trie.findChild(ba, 't');
        assertTrue(trie.isSyllableEnd(bat));
        int c = trie.findChild(0, 'c');
        int ca = trie.findChild(c, 'a');
        assertTrue(trie.isSyllableEnd(ca));
    }

    // ---- 4. Forced base-collision case --------------------------------------

    /**
     * {"ab", "ba"} is deliberately shaped to force the builder's base search
     * to skip past its first candidate.
     *
     * charMap assigns mapped values by ascending codepoint, so 'a' -> 1 and
     * 'b' -> 2. Root places its two children (mapped 1 and 2) at
     * base(root)+1 and base(root)+2 — call these slots S_a and S_b.
     *
     * Whichever of the two single-letter nodes is converted *second* by the
     * BFS will try candidate base = 1 for its own single child first: node
     * 'a' has a child mapped 2 (word "ab"), which targets base+2; node 'b'
     * has a child mapped 1 (word "ba"), which targets base+1. By construction
     * S_a = base(root)+1 and S_b = base(root)+2, so a candidate base of 1 on
     * either node always targets the *other* node's already-committed slot
     * (S_b for node 'a', S_a for node 'b') — a guaranteed collision requiring
     * the search to advance to a later candidate. This holds regardless of
     * which of 'a'/'b' the BFS happens to visit first, since the situation is
     * symmetric.
     *
     * We only assert the round-trip (reachability/word-end/id) property here
     * — not the specific base chosen — since the exact base is an
     * implementation detail.
     */
    @Test
    void forcedBaseCollisionStillRoundTrips() {
        String[] words = {"ab", "ba"};
        WordTrie trie = DoubleArrayTrieBuilder.buildWordTrie(words);

        int a = trie.findChild(0, 'a');
        assertNotEquals(-1, a);
        assertFalse(trie.isWordEnd(a));
        int ab = trie.findChild(a, 'b');
        assertNotEquals(-1, ab);
        assertTrue(trie.isWordEnd(ab));
        assertEquals(0, trie.wordIdAt(ab));

        int b = trie.findChild(0, 'b');
        assertNotEquals(-1, b);
        assertFalse(trie.isWordEnd(b));
        int ba = trie.findChild(b, 'a');
        assertNotEquals(-1, ba);
        assertTrue(trie.isWordEnd(ba));
        assertEquals(1, trie.wordIdAt(ba));
    }

    // ---- 5. Round-trip property test over a synthetic word list ------------

    private static List<String> sortedUnique(String... raw) {
        List<String> list = new ArrayList<>(List.of(raw));
        list.sort(String::compareTo);
        List<String> dedup = new ArrayList<>();
        for (String s : list) {
            if (dedup.isEmpty() || !dedup.get(dedup.size() - 1).equals(s)) {
                dedup.add(s);
            }
        }
        return dedup;
    }

    private static final List<String> SYNTHETIC_WORDS = sortedUnique(
            "an", "anh", "ba", "ban", "bat", "bao", "ca", "cam", "can", "cha",
            "cho", "chua", "con", "cua", "di", "dia", "em", "gia", "gio", "ha",
            "hai", "hoa", "hoc", "hoi", "khong", "la", "lam", "le", "ly",
            "ma", "mai", "me", "mot", "mua", "na", "nam", "nao", "nha", "nhu",
            "no", "nu", "nuoc", "ong", "quoc", "ra", "sang", "so", "ta", "tam",
            "tay", "thi", "thu", "toi", "tot", "tu", "va", "vao", "về", "vi",
            "xa", "xanh", "xe", "đen", "đi", "đo", "đu");

    @Test
    void syntheticWordListRoundTrips() {
        List<String> words = SYNTHETIC_WORDS;
        WordTrie trie = DoubleArrayTrieBuilder.buildWordTrie(words);

        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            int node = walk(trie, word);
            assertNotEquals(-1, node, () -> "word not reachable: " + word);
            assertTrue(trie.isWordEnd(node), () -> "expected word end: " + word);
            assertEquals(i, trie.wordIdAt(node), () -> "wrong wordId for: " + word);

            // No strict, non-listed prefix of this word is falsely a word end.
            int[] cps = word.codePoints().toArray();
            int n = 0;
            for (int k = 0; k < cps.length - 1; k++) {
                n = trie.findChild(n, cps[k]);
                assertNotEquals(-1, n, () -> "prefix not reachable: " + word.substring(0, 0));
                String prefix = word.substring(0, k + 1);
                if (!words.contains(prefix)) {
                    int finalN = n;
                    assertFalse(trie.isWordEnd(finalN),
                            () -> "prefix falsely flagged as word end: " + prefix);
                }
            }
        }
    }

    @Test
    void syntheticSyllableListRoundTrips() {
        List<String> syllables = SYNTHETIC_WORDS; // same shape works fine as syllables too
        SyllableTrie trie = DoubleArrayTrieBuilder.buildSyllableTrie(syllables);

        for (String syllable : syllables) {
            int node = walk(trie, syllable);
            assertNotEquals(-1, node, () -> "syllable not reachable: " + syllable);
            assertTrue(trie.isSyllableEnd(node), () -> "expected syllable end: " + syllable);

            int[] cps = syllable.codePoints().toArray();
            int n = 0;
            for (int k = 0; k < cps.length - 1; k++) {
                n = trie.findChild(n, cps[k]);
                assertNotEquals(-1, n);
                String prefix = syllable.substring(0, k + 1);
                if (!syllables.contains(prefix)) {
                    int finalN = n;
                    assertFalse(trie.isSyllableEnd(finalN),
                            () -> "prefix falsely flagged as syllable end: " + prefix);
                }
            }
        }
    }

    private static int walk(WordTrie trie, String word) {
        int node = 0;
        for (int cp : word.codePoints().toArray()) {
            node = trie.findChild(node, cp);
            if (node == -1) return -1;
        }
        return node;
    }

    private static int walk(SyllableTrie trie, String word) {
        int node = 0;
        for (int cp : word.codePoints().toArray()) {
            node = trie.findChild(node, cp);
            if (node == -1) return -1;
        }
        return node;
    }

    // ---- Precondition failures ----------------------------------------------

    @Test
    void unsortedWordsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubleArrayTrieBuilder.buildWordTrie(new String[] {"ca", "ba"}));
    }

    @Test
    void duplicateWordsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubleArrayTrieBuilder.buildWordTrie(new String[] {"ba", "ba", "ca"}));
    }

    @Test
    void unsortedSyllablesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubleArrayTrieBuilder.buildSyllableTrie(new String[] {"ca", "ba"}));
    }

    @Test
    void duplicateSyllablesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubleArrayTrieBuilder.buildSyllableTrie(new String[] {"ba", "ba", "ca"}));
    }
}
