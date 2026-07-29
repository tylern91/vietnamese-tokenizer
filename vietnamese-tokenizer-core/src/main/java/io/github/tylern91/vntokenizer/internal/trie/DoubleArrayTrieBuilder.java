package io.github.tylern91.vntokenizer.internal.trie;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeSet;

/**
 * Builds the {@code charMap}/{@code base}/{@code parent} encoding consumed by
 * {@link WordTrie} and {@link SyllableTrie} from a sorted, duplicate-free word
 * or syllable list.
 *
 * <p>Construction happens in two phases: an ordinary prefix trie is built
 * first (no double-array concerns at all), then that trie is converted to the
 * double-array encoding via BFS. For each node, the smallest non-negative
 * {@code base} is chosen such that every child's target slot
 * ({@code base + mappedChar}) is simultaneously free; the search always
 * terminates because the arrays grow without bound, so already-placed nodes
 * never need to be relocated.
 */
public final class DoubleArrayTrieBuilder {

    private DoubleArrayTrieBuilder() {
    }

    public static WordTrie buildWordTrie(String[] words) {
        return buildWordTrie(Arrays.asList(words));
    }

    /**
     * @param words sorted, duplicate-free words; each word's {@code wordId} is
     *              its index in this list ({@link io.github.tylern91.vntokenizer.internal.dict.DictLoader}
     *              keys unigram frequencies by this same index).
     */
    public static WordTrie buildWordTrie(List<String> words) {
        validateSortedNoDuplicates(words);
        int[] charMap = buildCharMap(words);
        OrdinaryNode root = buildOrdinaryTrie(words, charMap, true);
        Encoded encoded = encode(root);

        int[] wordId = new int[encoded.poolSize];
        Arrays.fill(wordId, -1);
        for (Map.Entry<OrdinaryNode, Integer> e : encoded.slotOf.entrySet()) {
            if (e.getKey().wordId >= 0) {
                wordId[e.getValue()] = e.getKey().wordId;
            }
        }
        return new WordTrie(charMap, encoded.base, encoded.parent, wordId);
    }

    public static SyllableTrie buildSyllableTrie(String[] syllables) {
        return buildSyllableTrie(Arrays.asList(syllables));
    }

    public static SyllableTrie buildSyllableTrie(List<String> syllables) {
        validateSortedNoDuplicates(syllables);
        int[] charMap = buildCharMap(syllables);
        OrdinaryNode root = buildOrdinaryTrie(syllables, charMap, false);
        Encoded encoded = encode(root);

        boolean[] terminal = new boolean[encoded.poolSize];
        for (Map.Entry<OrdinaryNode, Integer> e : encoded.slotOf.entrySet()) {
            if (e.getKey().terminal) {
                terminal[e.getValue()] = true;
            }
        }
        return new SyllableTrie(charMap, encoded.base, encoded.parent, terminal);
    }

    private static void validateSortedNoDuplicates(List<String> items) {
        for (int i = 1; i < items.size(); i++) {
            int cmp = items.get(i - 1).compareTo(items.get(i));
            if (cmp == 0) {
                throw new IllegalArgumentException("Duplicate entry: \"" + items.get(i) + "\"");
            }
            if (cmp > 0) {
                throw new IllegalArgumentException("Input must be sorted lexicographically: \""
                        + items.get(i - 1) + "\" precedes \"" + items.get(i) + "\" out of order");
            }
        }
    }

    /** Maps every codepoint appearing in {@code items} to a distinct value starting at 1 (0 = unmapped). */
    private static int[] buildCharMap(List<String> items) {
        TreeSet<Integer> codepoints = new TreeSet<>();
        for (String item : items) {
            item.codePoints().forEach(codepoints::add);
        }
        int maxCodepoint = codepoints.isEmpty() ? -1 : codepoints.last();
        int[] charMap = new int[Math.max(maxCodepoint + 1, 1)];
        int next = 1;
        for (int cp : codepoints) {
            charMap[cp] = next++;
        }
        return charMap;
    }

    private static OrdinaryNode buildOrdinaryTrie(List<String> items, int[] charMap, boolean assignWordIds) {
        OrdinaryNode root = new OrdinaryNode();
        for (int i = 0; i < items.size(); i++) {
            OrdinaryNode cur = root;
            for (int cp : items.get(i).codePoints().toArray()) {
                int mapped = charMap[cp];
                cur = cur.children.computeIfAbsent(mapped, k -> new OrdinaryNode());
            }
            if (assignWordIds) {
                cur.wordId = i;
            } else {
                cur.terminal = true;
            }
        }
        return root;
    }

    /** Converts an ordinary prefix trie into the double-array encoding via BFS base-assignment. */
    private static Encoded encode(OrdinaryNode root) {
        Map<OrdinaryNode, Integer> slotOf = new HashMap<>();
        slotOf.put(root, 0);

        int[] base = new int[4];
        int[] parent = new int[4];
        Arrays.fill(parent, -1);
        int poolSize = 1;

        Queue<OrdinaryNode> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            OrdinaryNode cur = queue.poll();
            int curId = slotOf.get(cur);
            if (cur.children.isEmpty()) {
                continue;
            }

            List<Integer> mappedValues = new ArrayList<>(cur.children.keySet());
            int maxMapped = Collections.max(mappedValues);

            int b = 0;
            while (true) {
                int maxTarget = b + maxMapped;
                if (maxTarget >= base.length) {
                    int oldLength = base.length;
                    int newLength = oldLength;
                    while (newLength <= maxTarget) {
                        newLength *= 2;
                    }
                    base = Arrays.copyOf(base, newLength);
                    parent = Arrays.copyOf(parent, newLength);
                    Arrays.fill(parent, oldLength, newLength, -1);
                }
                boolean allFree = true;
                for (int m : mappedValues) {
                    if (parent[b + m] != -1) {
                        allFree = false;
                        break;
                    }
                }
                if (allFree) {
                    break;
                }
                b++;
            }

            base[curId] = b;
            for (Map.Entry<Integer, OrdinaryNode> e : cur.children.entrySet()) {
                int t = b + e.getKey();
                parent[t] = curId;
                slotOf.put(e.getValue(), t);
                poolSize = Math.max(poolSize, t + 1);
                queue.add(e.getValue());
            }
        }

        return new Encoded(Arrays.copyOf(base, poolSize), Arrays.copyOf(parent, poolSize), poolSize, slotOf);
    }

    private static final class OrdinaryNode {
        final Map<Integer, OrdinaryNode> children = new HashMap<>();
        int wordId = -1;
        boolean terminal = false;
    }

    private static final class Encoded {
        final int[] base;
        final int[] parent;
        final int poolSize;
        final Map<OrdinaryNode, Integer> slotOf;

        Encoded(int[] base, int[] parent, int poolSize, Map<OrdinaryNode, Integer> slotOf) {
            this.base = base;
            this.parent = parent;
            this.poolSize = poolSize;
            this.slotOf = slotOf;
        }
    }
}
