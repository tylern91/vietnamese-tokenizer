package io.github.tylern91.vntokenizer.internal.score;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BigramScoresTest {

    @Test
    void emptyReturnsDefaultForAnyPair() {
        BigramScores scores = BigramScores.empty();
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(0, 1));
    }

    @Test
    void defaultScoreIsZero() {
        assertEquals(0.0f, BigramScores.DEFAULT_SCORE);
    }

    @Test
    void knownPairReturnsStoredScore() {
        BigramScores scores = BigramScores.of(Map.of(
                BigramScores.key(1, 2), 0.85f,
                BigramScores.key(2, 3), 0.42f));
        assertEquals(0.85f, scores.score(1, 2));
        assertEquals(0.42f, scores.score(2, 3));
    }

    @Test
    void unknownPairReturnsDefault() {
        BigramScores scores = BigramScores.of(Map.of(BigramScores.key(1, 2), 0.85f));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(9, 9));
    }

    @Test
    void orderMattersForPair() {
        // (1,2) is distinct from (2,1)
        BigramScores scores = BigramScores.of(Map.of(BigramScores.key(1, 2), 0.85f));
        assertEquals(BigramScores.DEFAULT_SCORE, scores.score(2, 1));
    }

    @Test
    void negativeIdsDoNotCollideWithPositiveIds() {
        BigramScores scores = BigramScores.of(Map.of(
                BigramScores.key(-1, -2), 0.5f,
                BigramScores.key(1, 2), 0.9f));
        assertEquals(0.5f, scores.score(-1, -2));
        assertEquals(0.9f, scores.score(1, 2));
    }
}
