package io.github.tylern91.vntokenizer;

public record Token(String text, Token.Type type, int start, int end) {

    public enum Type {
        WORD, NUMBER, PUNCT, SPACE
    }
}
