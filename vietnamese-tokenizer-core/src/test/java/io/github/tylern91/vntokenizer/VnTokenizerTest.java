package io.github.tylern91.vntokenizer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VnTokenizerTest {

    @AfterEach
    void resetCache() {
        VnTokenizer.resetInstances();
    }

    @Test
    void bundledSingletonIdentity() {
        VnTokenizer a = VnTokenizer.getInstance();
        VnTokenizer b = VnTokenizer.getInstance();
        assertSame(a, b);
    }

    @Test
    void customPathSingletonIdentity() {
        Path p = Path.of("/tmp/dicts");
        VnTokenizer a = VnTokenizer.getInstance(p);
        VnTokenizer b = VnTokenizer.getInstance(p);
        assertSame(a, b);
    }

    @Test
    void differentPathsProduceDifferentInstances() {
        VnTokenizer bundled = VnTokenizer.getInstance();
        VnTokenizer custom = VnTokenizer.getInstance(Path.of("/tmp/dicts"));
        assertNotSame(bundled, custom);
    }

    @Test
    void tokenizeThrowsUnsupported() {
        VnTokenizer t = VnTokenizer.getInstance();
        assertThrows(UnsupportedOperationException.class, () -> t.tokenize("xin chào"));
    }

    @Test
    void tokenizeWithOptionThrowsUnsupported() {
        VnTokenizer t = VnTokenizer.getInstance();
        assertThrows(UnsupportedOperationException.class,
            () -> t.tokenize("xin chào", TokenizeOption.NORMAL));
    }

    @Test
    void tokenizeToStringsThrowsUnsupported() {
        VnTokenizer t = VnTokenizer.getInstance();
        assertThrows(UnsupportedOperationException.class, () -> t.tokenizeToStrings("xin chào"));
    }
}
