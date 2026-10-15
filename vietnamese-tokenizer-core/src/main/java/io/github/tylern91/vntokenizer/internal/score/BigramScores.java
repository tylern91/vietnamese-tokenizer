package io.github.tylern91.vntokenizer.internal.score;

import java.util.Map;

/**
 * Immutable additive bigram scores keyed by (prevWordId, nextWordId).
 * Missing pairs fall back to {@link #DEFAULT_SCORE} — this is a bonus added
 * on top of the base path score during Phase-4 decoding, not a penalty.
 *
 * <p>Stored in an open-addressing table of primitive arrays: at corpus scale (millions of
 * pairs) a boxed {@code Map<Long, Float>} costs hundreds of megabytes. The packed keys have a
 * degenerate {@link Long#hashCode()} (see {@link #key}), so slots are chosen by {@link #mix}.
 */
public final class BigramScores {

    public static final float DEFAULT_SCORE = 0.0f;

    private final long[] keys;
    private final float[] values;
    private final boolean[] occupied;
    private final int mask;

    private BigramScores(long[] keys, float[] values, boolean[] occupied) {
        this.keys = keys;
        this.values = values;
        this.occupied = occupied;
        this.mask = keys.length - 1;
    }

    public static BigramScores empty() {
        return builder().build();
    }

    public static BigramScores of(Map<Long, Float> scores) {
        Builder builder = builder();
        for (Map.Entry<Long, Float> e : scores.entrySet()) {
            builder.putKey(e.getKey(), e.getValue());
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Accumulates scores into primitive arrays. A later {@code put} for the same pair replaces the
     * earlier one. Not reusable after {@link #build()}.
     */
    public static final class Builder {

        private static final int MIN_CAPACITY = 16;
        // Linear probing stays fast up to roughly this load; above it the table doubles.
        private static final double MAX_LOAD = 0.7;

        private long[] keys = new long[MIN_CAPACITY];
        private float[] values = new float[MIN_CAPACITY];
        private boolean[] occupied = new boolean[MIN_CAPACITY];
        private int size;

        public Builder put(int prevId, int nextId, float score) {
            putKey(key(prevId, nextId), score);
            return this;
        }

        private void putKey(long key, float score) {
            if (size + 1 > keys.length * MAX_LOAD) {
                grow();
            }
            int slot = mix(key) & (keys.length - 1);
            while (occupied[slot]) {
                if (keys[slot] == key) {
                    values[slot] = score;
                    return;
                }
                slot = (slot + 1) & (keys.length - 1);
            }
            occupied[slot] = true;
            keys[slot] = key;
            values[slot] = score;
            size++;
        }

        public BigramScores build() {
            return new BigramScores(keys, values, occupied);
        }

        private void grow() {
            long[] oldKeys = keys;
            float[] oldValues = values;
            boolean[] oldOccupied = occupied;
            int capacity = oldKeys.length * 2;
            keys = new long[capacity];
            values = new float[capacity];
            occupied = new boolean[capacity];
            for (int i = 0; i < oldKeys.length; i++) {
                if (!oldOccupied[i]) {
                    continue;
                }
                int slot = mix(oldKeys[i]) & (capacity - 1);
                while (occupied[slot]) {
                    slot = (slot + 1) & (capacity - 1);
                }
                occupied[slot] = true;
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
            }
        }
    }

    /**
     * Packs an ordered (prev, next) word-id pair into a single lookup key.
     * Order matters: {@code key(a, b) != key(b, a)}.
     */
    public static long key(int prevId, int nextId) {
        return ((long) prevId << 32) | (nextId & 0xffffffffL);
    }

    /** MurmurHash3's 64-bit finalizer: spreads the packed (prev, next) keys evenly. */
    private static int mix(long key) {
        long h = key;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) h;
    }

    public float score(int prevId, int nextId) {
        long key = key(prevId, nextId);
        int slot = mix(key) & mask;
        while (occupied[slot]) {
            if (keys[slot] == key) {
                return values[slot];
            }
            slot = (slot + 1) & mask;
        }
        return DEFAULT_SCORE;
    }
}
