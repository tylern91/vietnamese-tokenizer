package io.github.tylern91.vntokenizer.acceptance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tylern91.vntokenizer.internal.dict.DictLoader;
import io.github.tylern91.vntokenizer.internal.dict.Dictionaries;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoadAcceptanceTest {

    private static final Duration LOAD_BUDGET = Duration.ofSeconds(10);

    @Test
    @DisplayName("[AC-1] loading the bundled dictionaries completes in under 10 seconds")
    void bundledDictionariesLoadWithinBudget() {
        long startNanos = System.nanoTime();
        Dictionaries dictionaries = DictLoader.load();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertNotNull(dictionaries);
        assertTrue(elapsed.compareTo(LOAD_BUDGET) < 0,
                () -> "DictLoader.load() took " + elapsed.toMillis() + " ms, budget " + LOAD_BUDGET.toMillis()
                        + " ms");
    }
}
