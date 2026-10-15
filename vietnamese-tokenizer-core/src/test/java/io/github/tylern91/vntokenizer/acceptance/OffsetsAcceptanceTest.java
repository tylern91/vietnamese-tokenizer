package io.github.tylern91.vntokenizer.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tylern91.vntokenizer.Token;
import io.github.tylern91.vntokenizer.TokenizeOption;
import io.github.tylern91.vntokenizer.VnTokenizer;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OffsetsAcceptanceTest {

    private static VnTokenizer fixtureTokenizer(Path dir) {
        return AcceptanceFixtures.tokenizerFor(dir,
                List.of("hà nội\t50", "học\t30", "nam\t20", "sinh\t20", "việt nam\t40", "xin chào\t10"),
                List.of("chào", "hà", "học", "nam", "nội", "ok", "sinh", "việt", "xin"),
                List.of("xin chào\tviệt nam\t5"));
    }

    private static int codepoints(String s) {
        return s.codePointCount(0, s.length());
    }

    private static String nfc(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    private static String nfd(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD);
    }

    /** Codepoint slice {@code [start, end)} of {@code s}. */
    private static String slice(String s, int start, int end) {
        int from = s.offsetByCodePoints(0, start);
        int to = s.offsetByCodePoints(from, end - start);
        return s.substring(from, to);
    }

    @Test
    @DisplayName("[AC-4] offsets of NFD input index the original input while token texts are NFC")
    void nfdInputOffsetsIndexTheOriginal(@TempDir Path dir) {
        VnTokenizer tokenizer = fixtureTokenizer(dir);
        String input = nfd("Việt Nam ok");
        assertEquals(13, codepoints(input), "precondition: NFD form of the input is 13 codepoints");

        List<Token> tokens = tokenizer.tokenize(input);

        assertFalse(tokens.isEmpty());
        assertEquals(0, tokens.get(0).start());
        assertEquals(13, tokens.get(tokens.size() - 1).end());
        StringBuilder joined = new StringBuilder();
        for (Token token : tokens) {
            assertEquals(nfc(token.text()), token.text(), "token text must be NFC: " + token);
            assertEquals(nfc(slice(input, token.start(), token.end())), token.text(),
                    "offsets must delimit the token inside the ORIGINAL input: " + token);
            joined.append(token.text());
        }
        assertEquals(nfc(input), joined.toString());
    }

    private static List<String> mixedInputs() {
        List<String> nfcInputs = List.of(
                "Xin chào Việt Nam",
                "Hà Nội",
                "Tôi yêu Việt Nam.",
                "Nguyễn Văn A",
                "đường Lê Lợi",
                "ĐẠI HỌC QUỐC GIA",
                "ước mơ thuở nhỏ",
                "Thầy bảo: \"Học sinh giỏi!\"",
                "ặ ế ổ ữ ỷ ỵ",
                "học sinh, sinh viên; giáo viên?");
        List<String> inputs = new ArrayList<>(nfcInputs);
        for (String input : nfcInputs) {
            inputs.add(nfd(input));
        }
        inputs.addAll(List.of(
                "Hello 😀 world",
                "😀😀😀",
                "a😀b",
                "Việt😀Nam 123 ok",
                "👨‍👩‍👧 gia đình",
                nfd("Việt😀Nam ở Hà Nội 😀"),
                "xem https://a.vn/x.",
                "www.vnexpress.net",
                "(www.vnexpress.net)",
                "https://a.vn,",
                nfd("đọc http://hà.nội/trang-chủ, rồi về"),
                "gửi tới a.b@c.com!",
                "nay.Toi",
                "report.pdf v1.2.beta",
                "123",
                "1.234,56 đồng",
                "2024-01-02",
                "0912345678",
                "...",
                "!!",
                "?!?!",
                "((()))",
                "— – …",
                "a b",
                "",
                " ",
                "   ",
                "\t\n",
                " Việt Nam ",
                "x".repeat(300)));
        return inputs;
    }

    @Test
    @DisplayName("[AC-5] tokens tile the original input contiguously and their texts concatenate to its NFC form")
    void tokensTileTheOriginalInput(@TempDir Path dir) {
        VnTokenizer tokenizer = fixtureTokenizer(dir);
        List<String> inputs = mixedInputs();
        assertTrue(inputs.size() >= 50, "precondition: at least 50 inputs, got " + inputs.size());

        List<String> violations = new ArrayList<>();
        for (String input : inputs) {
            for (TokenizeOption option : TokenizeOption.values()) {
                checkInvariants(tokenizer.tokenize(input, option), input, option, violations);
            }
        }

        assertTrue(violations.isEmpty(), () -> violations.size() + " violation(s):\n" + String.join("\n", violations));
    }

    private static void checkInvariants(List<Token> tokens, String input, TokenizeOption option,
            List<String> violations) {
        String label = option + " " + escape(input) + ": ";
        int length = codepoints(input);
        if (tokens.isEmpty()) {
            if (length != 0) {
                violations.add(label + "no tokens for non-empty input");
            }
            return;
        }
        if (tokens.get(0).start() != 0) {
            violations.add(label + "first.start=" + tokens.get(0).start());
        }
        int last = tokens.get(tokens.size() - 1).end();
        if (last != length) {
            violations.add(label + "last.end=" + last + " expected " + length);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.end() <= token.start()) {
                violations.add(label + "empty or inverted token " + token);
            }
            if (i > 0 && token.start() != tokens.get(i - 1).end()) {
                violations.add(label + "gap/overlap before " + token + " after " + tokens.get(i - 1));
            }
            joined.append(token.text());
        }
        if (!nfc(input).equals(joined.toString())) {
            violations.add(label + "texts concatenate to " + escape(joined.toString()));
        }
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder("\"");
        s.codePoints().forEach(cp -> {
            if (cp < 0x20 || cp > 0x7e) {
                out.append(String.format("\\u{%X}", cp));
            } else {
                out.append((char) cp);
            }
        });
        String escaped = out.append('"').toString();
        return escaped.length() > 120 ? escaped.substring(0, 120) + "..." : escaped;
    }
}
