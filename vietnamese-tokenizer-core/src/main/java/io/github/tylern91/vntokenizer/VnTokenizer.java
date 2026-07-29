package io.github.tylern91.vntokenizer;

import io.github.tylern91.vntokenizer.internal.decode.ViterbiSegmenter;
import io.github.tylern91.vntokenizer.internal.dict.DictLoader;
import io.github.tylern91.vntokenizer.internal.dict.Dictionaries;
import io.github.tylern91.vntokenizer.internal.norm.VnNormalizer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VnTokenizer {

    private static final Path BUNDLED_SENTINEL = Path.of("__bundled__");
    private static final ConcurrentHashMap<Path, VnTokenizer> CACHE = new ConcurrentHashMap<>();

    // Matched against the normalized text rather than the raw input, so a match's UTF-16 char
    // offset can double as a codepoint offset — safe because Vietnamese and URL/host syntax never
    // use supplementary-plane characters.
    private static final Pattern URL_PATTERN =
            Pattern.compile("\\b(?:https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);
    // Repetition counts bounded to RFC 1035 hostname structural limits (label <= 63 octets,
    // <= 127 labels, total <= 253 octets) so the regex engine's recursive backtracking on
    // `(?:group)+` can't blow the call stack on adversarial input with thousands of labels.
    private static final Pattern HOST_PATTERN =
            Pattern.compile("\\b(?:[a-zA-Z0-9-]{1,63}\\.){1,127}[a-zA-Z]{2,63}\\b");

    private final Dictionaries dictionaries;
    private final ViterbiSegmenter segmenter;

    private VnTokenizer(Dictionaries dictionaries) {
        this.dictionaries = dictionaries;
        this.segmenter = new ViterbiSegmenter(dictionaries);
    }

    public static VnTokenizer getInstance() {
        return CACHE.computeIfAbsent(BUNDLED_SENTINEL, k -> new VnTokenizer(DictLoader.load()));
    }

    public static VnTokenizer getInstance(Path dictDir) {
        return CACHE.computeIfAbsent(dictDir, k -> new VnTokenizer(DictLoader.load(dictDir)));
    }

    public List<Token> tokenize(String text) {
        return tokenize(text, TokenizeOption.NORMAL);
    }

    public List<Token> tokenize(String text, TokenizeOption option) {
        int[] normalized = VnNormalizer.normalize(text.codePoints().toArray());
        return switch (option) {
            case NORMAL -> segmentRange(normalized, 0, normalized.length);
            case HOST -> segmentWithAtoms(normalized, HOST_PATTERN);
            case URL -> segmentWithAtoms(normalized, URL_PATTERN);
        };
    }

    public List<String> tokenizeToStrings(String text) {
        List<Token> tokens = tokenize(text);
        List<String> texts = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            texts.add(token.text());
        }
        return texts;
    }

    /**
     * Pre-splits {@code normalized} on {@code atomPattern} matches — kept as single atomic
     * {@code WORD} tokens, since {@link Token.Type} has no dedicated URL/host category — and
     * word-segments the text between matches (Decision 6).
     */
    private List<Token> segmentWithAtoms(int[] normalized, Pattern atomPattern) {
        String text = new String(normalized, 0, normalized.length);
        Matcher matcher = atomPattern.matcher(text);

        List<Token> tokens = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            tokens.addAll(segmentRange(normalized, cursor, start));
            tokens.add(new Token(matcher.group(), Token.Type.WORD, start, end));
            cursor = end;
        }
        tokens.addAll(segmentRange(normalized, cursor, normalized.length));
        return tokens;
    }

    private List<Token> segmentRange(int[] normalized, int start, int end) {
        if (start >= end) {
            return List.of();
        }
        int[] slice = Arrays.copyOfRange(normalized, start, end);
        List<ViterbiSegmenter.Span> spans = segmenter.segment(slice);
        return toTokens(slice, spans, start);
    }

    /**
     * Converts raw DP spans into {@link Token}s, merging consecutive OOV spans of the same
     * classified type into one wider token — the segmenter can emit many adjacent
     * single-codepoint OOV spans (e.g. one per digit), which would otherwise fragment a run a
     * caller expects merged.
     */
    private List<Token> toTokens(int[] codepoints, List<ViterbiSegmenter.Span> spans, int offset) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < spans.size()) {
            ViterbiSegmenter.Span span = spans.get(i);
            if (span.wordId() != ViterbiSegmenter.OOV_WORD_ID) {
                tokens.add(toToken(codepoints, span.start(), span.end(), Token.Type.WORD, offset));
                i++;
                continue;
            }

            Token.Type type = classifyOov(codepoints, span.start(), span.end());
            int runEnd = span.end();
            int j = i + 1;
            while (j < spans.size() && spans.get(j).wordId() == ViterbiSegmenter.OOV_WORD_ID
                    && classifyOov(codepoints, spans.get(j).start(), spans.get(j).end()) == type) {
                runEnd = spans.get(j).end();
                j++;
            }
            tokens.add(toToken(codepoints, span.start(), runEnd, type, offset));
            i = j;
        }
        return tokens;
    }

    private static Token toToken(int[] codepoints, int start, int end, Token.Type type, int offset) {
        String text = new String(codepoints, start, end - start);
        return new Token(text, type, offset + start, offset + end);
    }

    /**
     * OOV spans from {@link ViterbiSegmenter} are always exactly one codepoint or one recognized
     * syllable (never a mixed-class run), so classifying only the first codepoint is sufficient.
     */
    private Token.Type classifyOov(int[] codepoints, int start, int end) {
        int first = codepoints[start];
        if (Character.isWhitespace(first)) {
            return Token.Type.SPACE;
        }
        if (Character.isDigit(first)) {
            return Token.Type.NUMBER;
        }
        if (Character.isLetter(first) || dictionaries.syllableTrie().containsSyllable(codepoints, start, end)) {
            return Token.Type.WORD;
        }
        return Token.Type.PUNCT;
    }

    static void resetInstances() {
        CACHE.clear();
    }
}
