package io.github.tylern91.vntokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenizeOptionTest {

    @Test
    void enumCount() {
        assertEquals(3, TokenizeOption.values().length);
    }

    @Test
    void ordinals() {
        assertEquals(0, TokenizeOption.NORMAL.ordinal());
        assertEquals(1, TokenizeOption.HOST.ordinal());
        assertEquals(2, TokenizeOption.URL.ordinal());
    }

    @Test
    void valueOfRoundTrip() {
        for (TokenizeOption opt : TokenizeOption.values()) {
            assertEquals(opt, TokenizeOption.valueOf(opt.name()));
        }
    }
}
