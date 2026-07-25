package io.github.tylern91.vntokenizer.internal.norm;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VnNormalizerTest {

    private static int[] toCodepoints(String s) {
        return s.codePoints().toArray();
    }

    private static String toStr(int[] codepoints) {
        return new String(codepoints, 0, codepoints.length);
    }

    /** Independently composes base+marks via the JDK, without touching VnNormalizer's own tables. */
    private static String composeReference(int base, int... marks) {
        StringBuilder sb = new StringBuilder().appendCodePoint(base);
        for (int m : marks) sb.appendCodePoint(m);
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC);
    }

    private static void assertMatchesNfcOracle(String word) {
        int[] nfd = toCodepoints(Normalizer.normalize(word, Normalizer.Form.NFD));
        int[] result = VnNormalizer.normalize(nfd);
        String expectedNfc = Normalizer.normalize(word, Normalizer.Form.NFC);
        assertEquals(expectedNfc, toStr(result));
    }

    @Test
    void matchesNfcOracleForCommonWords() {
        assertMatchesNfcOracle("Việt Nam");
        assertMatchesNfcOracle("Xin chào");
        assertMatchesNfcOracle("Tiếng Việt");
        assertMatchesNfcOracle("được");
        assertMatchesNfcOracle("nghiêng");
        assertMatchesNfcOracle("thượng");
        assertMatchesNfcOracle("dấu hỏi");
        assertMatchesNfcOracle("ngã ba");
        assertMatchesNfcOracle("Đà Nẵng");
    }

    @Test
    void idempotentOnAlreadyNfcInput() {
        String word = "Việt Nam";
        int[] nfc = toCodepoints(Normalizer.normalize(word, Normalizer.Form.NFC));
        assertEquals(word, toStr(VnNormalizer.normalize(nfc)));
    }

    @Test
    void asciiPassesThroughUnchanged() {
        String text = "Hello, World! 123";
        assertEquals(text, toStr(VnNormalizer.normalize(toCodepoints(text))));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals(0, VnNormalizer.normalize(new int[0]).length);
    }

    @Test
    void allSixTonesOnBareA() {
        assertEquals(composeReference('a'), toStr(VnNormalizer.normalize(new int[]{'a'})));
        assertEquals(composeReference('a', 0x0300), toStr(VnNormalizer.normalize(new int[]{'a', 0x0300})));
        assertEquals(composeReference('a', 0x0301), toStr(VnNormalizer.normalize(new int[]{'a', 0x0301})));
        assertEquals(composeReference('a', 0x0309), toStr(VnNormalizer.normalize(new int[]{'a', 0x0309})));
        assertEquals(composeReference('a', 0x0303), toStr(VnNormalizer.normalize(new int[]{'a', 0x0303})));
        assertEquals(composeReference('a', 0x0323), toStr(VnNormalizer.normalize(new int[]{'a', 0x0323})));
    }

    @Test
    void hatPlusToneComposesFully() {
        // a + circumflex + acute
        assertEquals(composeReference('a', 0x0302, 0x0301),
                toStr(VnNormalizer.normalize(new int[]{'a', 0x0302, 0x0301})));
        // o + horn + grave
        assertEquals(composeReference('o', 0x031B, 0x0300),
                toStr(VnNormalizer.normalize(new int[]{'o', 0x031B, 0x0300})));
    }

    @Test
    void hatAloneWithNoToneComposes() {
        assertEquals(composeReference('o', 0x031B), toStr(VnNormalizer.normalize(new int[]{'o', 0x031B})));
    }

    @Test
    void alreadyPrecomposedHatLetterPlusToneComposes() {
        // â (precomposed, U+00E2) + acute — partially-decomposed input
        assertEquals(composeReference('a', 0x0302, 0x0301),
                toStr(VnNormalizer.normalize(new int[]{0x00E2, 0x0301})));
    }

    @Test
    void invalidHatComboLeavesMarkUnconsumed() {
        // 'i' has no valid hat mark — the horn mark should pass through untouched
        int[] input = {'i', 0x031B};
        int[] result = VnNormalizer.normalize(input);
        assertArrayEquals(input, result);
    }
}
