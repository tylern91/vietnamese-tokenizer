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
     * <p>This and {@link #CODEPOINT_FALLBACK_PENALTY} set how eagerly the segmenter guesses at
     * unknown text instead of trusting the dictionary. They sit far below any real unigram
     * log-prob (which cluster around -5 to -15 for this corpus) so dictionary matches win when
     * available, with the syllable-level penalty softer than the codepoint-level one so a
     * recognized syllable is preferred over shredding it into individual codepoints.
     *
     * <p>Measured against UD_Vietnamese-VTB dev: a 400-configuration grid over whitespace
     * (0 to -50), syllable (-10 to -30), codepoint (-30 to -60) and bigram weight (0 to 1.5)
     * spans word-span F1 0.8956-0.8958, inside the 0.0005 noise tolerance, so these values were
     * kept.
     */
    private static final double SYLLABLE_FALLBACK_PENALTY = -25.0;

    /** Score for a single-codepoint OOV edge — the connectivity floor of last resort. */
    private static final double CODEPOINT_FALLBACK_PENALTY = -50.0;

    private final Dictionaries dictionaries;

    private final Penalties penalties;

    public ViterbiSegmenter(Dictionaries dictionaries) {
        this(dictionaries, Penalties.DEFAULT);
    }

    /**
     * Edge scores of the OOV fallbacks and the weight of the bigram bonus.
     *
     * @param whitespace  score of the edge that consumes one whitespace codepoint
     * @param syllable    score of an OOV edge spanning a recognized syllable
     * @param codepoint   score of any other single-codepoint OOV edge
     * @param bigramWeight multiplier applied to every bigram score
     */
    public record Penalties(double whitespace, double syllable, double codepoint, double bigramWeight) {

        public static final Penalties DEFAULT = new Penalties(
                CODEPOINT_FALLBACK_PENALTY, SYLLABLE_FALLBACK_PENALTY, CODEPOINT_FALLBACK_PENALTY, 1.0);
    }

    public ViterbiSegmenter(Dictionaries dictionaries, Penalties penalties) {
        this.dictionaries = dictionaries;
        this.penalties = penalties;
    }

    public List<Span> segment(int[] codepoints) {
        int n = codepoints.length;
        double[] bestScore = new double[n + 1];
        int[] backStart = new int[n + 1];
        int[] backWordId = new int[n + 1];
        // Word that supplies the bigram context for the edge leaving each position: the word
        // that ended there, carried across whitespace, or OOV_WORD_ID after any other OOV edge.
        int[] contextWordId = new int[n + 1];
        Arrays.fill(bestScore, Double.NEGATIVE_INFINITY);
        bestScore[0] = 0.0;
        backWordId[0] = OOV_WORD_ID; // beginning-of-sequence sentinel; never collides with a real
        contextWordId[0] = OOV_WORD_ID; // bigram key since BigramScores.key(prevId, nextId) packs
                                        // prevId into the upper 32 bits and only non-negative
                                        // prevIds occur in the real corpus.

        int[] nextNonLetter = computeNextNonLetter(codepoints);

        WordTrie wordTrie = dictionaries.wordTrie();
        for (int i = 0; i < n; i++) {
            int pos = i;
            int prevWordId = contextWordId[pos];
            double base = bestScore[pos];

            wordTrie.matchesFrom(codepoints, pos, (end, wordId) -> {
                if (!isLetterBoundary(codepoints, pos) || !isLetterBoundary(codepoints, end)) {
                    return;
                }
                double score = base + dictionaries.unigramLogProb(wordId)
                        + penalties.bigramWeight() * dictionaries.bigramScores().score(prevWordId, wordId);
                relax(bestScore, backStart, backWordId, contextWordId, pos, end, wordId, wordId, score);
            });

            relaxFallback(codepoints, pos, base, prevWordId, bestScore, backStart, backWordId, contextWordId,
                    nextNonLetter);
        }

        return backtrace(n, backStart, backWordId);
    }

    /**
     * True unless {@code index} falls between two letters, i.e. inside a syllable. A dictionary
     * word may only start and end on a boundary, otherwise "t" + "học sinh" could beat "thọc".
     */
    private static boolean isLetterBoundary(int[] codepoints, int index) {
        return index <= 0 || index >= codepoints.length
                || !Character.isLetter(codepoints[index - 1]) || !Character.isLetter(codepoints[index]);
    }

    /**
     * {@code nextNonLetter[k]} is the first index {@code >= k} holding a non-letter, or
     * {@code codepoints.length} if none remains — precomputed once so {@link #relaxFallback}
     * can look up a letter run's end in O(1) instead of re-scanning it from every position
     * within the run (which degrades to O(n^2) on a single long letter run). Stopping at any
     * non-letter, not just whitespace, keeps trailing punctuation out of a syllable edge.
     */
    private static int[] computeNextNonLetter(int[] codepoints) {
        int n = codepoints.length;
        int[] nextNonLetter = new int[n + 1];
        nextNonLetter[n] = n;
        for (int k = n - 1; k >= 0; k--) {
            nextNonLetter[k] = Character.isLetter(codepoints[k]) ? nextNonLetter[k + 1] : k;
        }
        return nextNonLetter;
    }

    private void relaxFallback(int[] codepoints, int i, double base, int prevWordId, double[] bestScore,
            int[] backStart, int[] backWordId, int[] contextWordId, int[] nextNonLetter) {
        // Whitespace keeps the previous word as bigram context so "w1 w2" scores as a bigram.
        boolean whitespace = Character.isWhitespace(codepoints[i]);
        int codepointContext = whitespace ? prevWordId : OOV_WORD_ID;
        relax(bestScore, backStart, backWordId, contextWordId, i, i + 1, OOV_WORD_ID, codepointContext,
                base + (whitespace ? penalties.whitespace() : penalties.codepoint()));

        int end = nextNonLetter[i + 1];
        if (end > i + 1 && dictionaries.syllableTrie().containsSyllable(codepoints, i, end)) {
            relax(bestScore, backStart, backWordId, contextWordId, i, end, OOV_WORD_ID, OOV_WORD_ID,
                    base + penalties.syllable());
        }
    }

    private static void relax(double[] bestScore, int[] backStart, int[] backWordId, int[] contextWordId, int start,
            int end, int wordId, int context, double score) {
        if (score > bestScore[end]) {
            bestScore[end] = score;
            backStart[end] = start;
            backWordId[end] = wordId;
            contextWordId[end] = context;
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
