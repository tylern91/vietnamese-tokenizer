package io.github.tylern91.vntokenizer;

/**
 * A single tokenized span of text, with its classified {@link Type} and codepoint offsets into
 * the original input.
 *
 * @param text  the token's original substring, with source casing preserved
 * @param type  the token's classification
 * @param start the codepoint offset of the first codepoint in {@code text}
 * @param end   the codepoint offset one past the last codepoint in {@code text}
 */
public record Token(String text, Token.Type type, int start, int end) {

    /** Classification assigned to a {@link Token}. */
    public enum Type {
        /** A word recognized by the dictionary, or a classified out-of-vocabulary run. */
        WORD,
        /** A run of digit codepoints. */
        NUMBER,
        /** A single punctuation codepoint. */
        PUNCT,
        /** A run of whitespace codepoints. */
        SPACE
    }
}
