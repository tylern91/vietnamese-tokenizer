package io.github.tylern91.vntokenizer.internal.decode;

import io.github.tylern91.vntokenizer.internal.dict.Dictionaries;
import io.github.tylern91.vntokenizer.internal.trie.WordTrie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Segments normalized codepoints into the best-scoring path through the word
 * lattice: at every position, {@link WordTrie#matchesFrom} supplies every
 * dictionary-word edge, and {@link #relaxFallback} adds OOV edges so a path
 * always exists even where the dictionary has nothing (Decision 5).
 */
public final class ViterbiSegmenter {

    /** Sentinel {@code wordId} for a span produced by an OOV fallback edge, not a dictionary match. */
    public static final int OOV_WORD_ID = -1;

    /**
     * Score for an OOV edge spanning a whole run recognized as a Vietnamese syllable
     * by {@code SyllableTrie} (but absent from the word dictionary itself).
     *
     * <p><b>Learning-mode contribution point:</b> this and {@link #CODEPOINT_FALLBACK_PENALTY}
     * are the tunable knob for how eagerly the segmenter falls back to guessing at unknown
     * text vs. trusting the dictionary. Too lenient (penalties close to 0) and genuine compound
     * words get needlessly fragmented into OOV runs instead of using real multi-word matches;
     * too harsh and legitimate gaps in the dictionary (rare proper nouns, neologisms) can't
     * compete with a nonsensical dictionary path that happens to also cover the span. The
     * current values are placeholders — deliberately far below any real unigram log-prob (which
     * cluster around -5 to -15 for this corpus) so dictionary matches always win when available,
     * with the syllable-level penalty softer than the codepoint-level one so a recognized
     * syllable is preferred over shredding it into individual codepoints.
     */
    private static final double SYLLABLE_FALLBACK_PENALTY = -25.0;

    /** Score for a single-codepoint OOV edge — the connectivity floor of last resort. */
    private static final double CODEPOINT_FALLBACK_PENALTY = -50.0;

    private final Dictionaries dictionaries;

    public ViterbiSegmenter(Dictionaries dictionaries) {
        this.dictionaries = dictionaries;
    }

    public List<Span> segment(int[] codepoints) {
        int n = codepoints.length;
        double[] bestScore = new double[n + 1];
        int[] backStart = new int[n + 1];
        int[] backWordId = new int[n + 1];
        Arrays.fill(bestScore, Double.NEGATIVE_INFINITY);
        bestScore[0] = 0.0;
        backWordId[0] = OOV_WORD_ID; // beginning-of-sequence sentinel; never collides with a real
                                     // bigram key since BigramScores.key(prevId, nextId) packs
                                     // prevId into the upper 32 bits and only non-negative prevIds
                                     // occur in the real corpus.

        int[] nextWhitespace = computeNextWhitespace(codepoints);

        WordTrie wordTrie = dictionaries.wordTrie();
        for (int i = 0; i < n; i++) {
            int pos = i;
            int prevWordId = backWordId[pos];
            double base = bestScore[pos];

            wordTrie.matchesFrom(codepoints, pos, (end, wordId) -> {
                double score = base + dictionaries.unigramLogProb(wordId)
                        + dictionaries.bigramScores().score(prevWordId, wordId);
                relax(bestScore, backStart, backWordId, pos, end, wordId, score);
            });

            relaxFallback(codepoints, pos, base, bestScore, backStart, backWordId, nextWhitespace);
        }

        return backtrace(n, backStart, backWordId);
    }

    /**
     * {@code nextWhitespace[k]} is the first index {@code >= k} holding whitespace, or
     * {@code codepoints.length} if none remains — precomputed once so {@link #relaxFallback}
     * can look up a whitespace-free run's end in O(1) instead of re-scanning it from every
     * position within the run (which degrades to O(n^2) on a single long whitespace-free run).
     */
    private static int[] computeNextWhitespace(int[] codepoints) {
        int n = codepoints.length;
        int[] nextWhitespace = new int[n + 1];
        nextWhitespace[n] = n;
        for (int k = n - 1; k >= 0; k--) {
            nextWhitespace[k] = Character.isWhitespace(codepoints[k]) ? k : nextWhitespace[k + 1];
        }
        return nextWhitespace;
    }

    private void relaxFallback(int[] codepoints, int i, double base, double[] bestScore, int[] backStart,
            int[] backWordId, int[] nextWhitespace) {
        relax(bestScore, backStart, backWordId, i, i + 1, OOV_WORD_ID, base + CODEPOINT_FALLBACK_PENALTY);

        int end = nextWhitespace[i + 1];
        if (end > i + 1 && dictionaries.syllableTrie().containsSyllable(codepoints, i, end)) {
            relax(bestScore, backStart, backWordId, i, end, OOV_WORD_ID, base + SYLLABLE_FALLBACK_PENALTY);
        }
    }

    private static void relax(double[] bestScore, int[] backStart, int[] backWordId, int start, int end, int wordId,
            double score) {
        if (score > bestScore[end]) {
            bestScore[end] = score;
            backStart[end] = start;
            backWordId[end] = wordId;
        }
    }

    private static List<Span> backtrace(int n, int[] backStart, int[] backWordId) {
        List<Span> spans = new ArrayList<>();
        int end = n;
        while (end > 0) {
            int start = backStart[end];
            spans.add(new Span(start, end, backWordId[end]));
            end = start;
        }
        Collections.reverse(spans);
        return spans;
    }

    public record Span(int start, int end, int wordId) {
    }
}
