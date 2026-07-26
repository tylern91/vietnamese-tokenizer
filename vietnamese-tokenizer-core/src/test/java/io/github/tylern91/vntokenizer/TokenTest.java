package io.github.tylern91.vntokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenTest {

    @Test
    void recordAccessors() {
        Token t = new Token("xin", Token.Type.WORD, 0, 3);
        assertEquals("xin", t.text());
        assertEquals(Token.Type.WORD, t.type());
        assertEquals(0, t.start());
        assertEquals(3, t.end());
    }

    @Test
    void recordEquality() {
        Token a = new Token("chào", Token.Type.WORD, 0, 4);
        Token b = new Token("chào", Token.Type.WORD, 0, 4);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality() {
        Token a = new Token("123", Token.Type.NUMBER, 0, 3);
        Token b = new Token("123", Token.Type.WORD, 0, 3);
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsFields() {
        Token t = new Token(".", Token.Type.PUNCT, 5, 6);
        String s = t.toString();
        assertTrue(s.contains("PUNCT"));
        assertTrue(s.contains("5"));
    }

    @Test
    void allEnumValues() {
        Token.Type[] types = Token.Type.values();
        assertEquals(4, types.length);
        assertArrayEquals(
            new Token.Type[]{Token.Type.WORD, Token.Type.NUMBER, Token.Type.PUNCT, Token.Type.SPACE},
            types
        );
    }

    @Test
    void enumValueOf() {
        assertEquals(Token.Type.WORD, Token.Type.valueOf("WORD"));
        assertEquals(Token.Type.SPACE, Token.Type.valueOf("SPACE"));
    }
}
