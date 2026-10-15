package io.github.tylern91.vntokenizer.internal.score;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies the primitive-array builder API: {@code BigramScores.builder()} returns a
 * {@code BigramScores.Builder} with {@code put(int prev, int next, float score)} and
 * {@code build()}. Lookups go through the existing {@code score(int, int)}.
 */
class BigramScoresBuilderTest {

    private static final int MANY = 100_000;

    @Test
    void emptyBuilderReturnsDefaultForAnyPair() {
        BigramScores scores = BigramScores.builder().build();

        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(0, 0));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(7, 3));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(-1, -1));
    }

    @Test
    void roundTripsStoredScoresAndDistinguishesPairOrder() {
        BigramScores.Builder builder = BigramScores.builder();
        builder.put(1, 2, 0.85f);
        builder.put(2, 1, -0.25f);
        builder.put(-1, -2, 0.5f);
        BigramScores scores = builder.build();

        assertEquals(0.85f, scores.score(1, 2));
        assertEquals(-0.25f, scores.score(2, 1));
        assertEquals(0.5f, scores.score(-1, -2));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(1, 1));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(9, 9));
    }

    @Test
    void keysDifferingOnlyInTheHighBitsAllRoundTrip() {
        BigramScores.Builder builder = BigramScores.builder();
        for (int prev = 0; prev < MANY; prev++) {
            builder.put(prev, 0, prev + 1.0f);
        }
        BigramScores scores = builder.build();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int prev = 0; prev < MANY; prev++) {
                assertEquals(prev + 1.0f, scores.score(prev, 0));
                assertEquals(BigramScores.DEFAULT_SCORE, scores.score(prev, 1));
            }
        });
    }

    @Test
    void keysWithIdenticalLongHashCodesAllRoundTrip() {
        // key(i, i) = (i << 32) | i has Long.hashCode() == 0 for every i, the worst case for a
        // table that indexes by hashCode.
        BigramScores.Builder builder = BigramScores.builder();
        for (int id = 0; id < MANY; id++) {
            builder.put(id, id, id + 1.0f);
        }
        BigramScores scores = builder.build();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int id = 0; id < MANY; id++) {
                assertEquals(id + 1.0f, scores.score(id, id));
                assertEquals(BigramScores.DEFAULT_SCORE, scores.score(id, id + 1));
            }
        });
    }

    @Test
    void oneMillionPairsStayWithinAPrimitiveArrayHeapBudget() {
        // A boxed Map<Long, Float> needs ~70-80 MB for this many pairs; primitive arrays ~27 MB.
        long budgetBytes = 45L * 1024 * 1024;
        long before = usedHeapAfterGc();

        BigramScores.Builder builder = BigramScores.builder();
        for (int i = 0; i < 1_000_000; i++) {
            builder.put(i / 1000, i % 1000, i);
        }
        BigramScores scores = builder.build();
        long retained = usedHeapAfterGc() - before;

        assertEquals(999_999f, scores.score(999, 999)); // keeps `scores` reachable across the measurement
        assertTrue(retained < budgetBytes, "retained " + retained / (1024 * 1024) + " MB, budget 45 MB");
    }

    private static long usedHeapAfterGc() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Test
    void keysDifferingOnlyInTheLowBitsAllRoundTrip() {
        BigramScores.Builder builder = BigramScores.builder();
        for (int next = 0; next < MANY; next++) {
            builder.put(5, next, next + 1.0f);
        }
        BigramScores scores = builder.build();

        for (int next = 0; next < MANY; next++) {
            assertEquals(next + 1.0f, scores.score(5, next));
        }
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(6, 0));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(5, MANY));
    }
}
