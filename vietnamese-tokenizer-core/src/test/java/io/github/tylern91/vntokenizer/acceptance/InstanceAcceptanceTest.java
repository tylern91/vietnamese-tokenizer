package io.github.tylern91.vntokenizer.acceptance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tylern91.vntokenizer.TokenizeOption;
import io.github.tylern91.vntokenizer.VnTokenizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceAcceptanceTest {

    private static VnTokenizer fixtureTokenizer(Path dir) {
        return AcceptanceFixtures.tokenizerFor(dir, List.of("ba\t10"), List.of("ba"), List.of());
    }

    @Test
    @DisplayName("[AC-7] equivalent spellings of one dictionary directory share a single instance")
    void equivalentPathsShareOneInstance(@TempDir Path dir) throws IOException {
        VnTokenizer direct = fixtureTokenizer(dir);
        Files.createDirectory(dir.resolve("x"));

        VnTokenizer viaDotDot = VnTokenizer.getInstance(dir.resolve("x/.."));

        assertSame(direct, viaDotDot);
    }

    /**
     * The message must name the parameter: exactly {@code text}/{@code option}, or begin with the
     * name followed by a space (e.g. {@code "text must not be null"}). The JDK's helpful-NPE text
     * ("Cannot invoke ...") does not qualify.
     */
    private static void assertNamesParameter(String name, NullPointerException e) {
        String message = e.getMessage();
        assertNotNull(message, "NullPointerException has no message");
        assertTrue(message.equals(name) || message.startsWith(name + " "),
                () -> "message should name parameter '" + name + "' but was: " + message);
    }

    @Test
    @DisplayName("[AC-8] a null text or option throws a NullPointerException naming the parameter")
    void nullArgumentsNameTheParameter(@TempDir Path dir) {
        VnTokenizer tokenizer = fixtureTokenizer(dir);

        assertNamesParameter("text", assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null)));
        assertNamesParameter("text",
                assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null, TokenizeOption.NORMAL)));
        assertNamesParameter("option",
                assertThrows(NullPointerException.class, () -> tokenizer.tokenize("a", null)));
    }
}
