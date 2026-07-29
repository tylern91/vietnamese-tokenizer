package io.github.tylern91.vntokenizer.internal.score;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable additive bigram scores keyed by (prevWordId, nextWordId).
 * Missing pairs fall back to {@link #DEFAULT_SCORE} — this is a bonus added
 * on top of the base path score during Phase-4 decoding, not a penalty.
 */
public final class BigramScores {

    public static final float DEFAULT_SCORE = 0.0f;

    private final Map<Long, Float> scores;

    private BigramScores(Map<Long, Float> scores) {
        this.scores = scores;
    }

    public static BigramScores empty() {
        return new BigramScores(Map.of());
    }

    public static BigramScores of(Map<Long, Float> scores) {
        // Map.copyOf builds a linear-probing ImmutableCollections.MapN with no treeification;
        // at corpus scale (millions of entries) the packed-long keys' degenerate hashCode
        // distribution (see key()) causes catastrophic probe-chain blowup. HashMap tolerates
        // this fine via bucket treeification.
        return new BigramScores(Collections.unmodifiableMap(new HashMap<>(scores)));
    }

    /**
     * Packs an ordered (prev, next) word-id pair into a single lookup key.
     * Order matters: {@code key(a, b) != key(b, a)}.
     */
    public static long key(int prevId, int nextId) {
        return ((long) prevId << 32) | (nextId & 0xffffffffL);
    }

    public float score(int prevId, int nextId) {
        return scores.getOrDefault(key(prevId, nextId), DEFAULT_SCORE);
    }
}
