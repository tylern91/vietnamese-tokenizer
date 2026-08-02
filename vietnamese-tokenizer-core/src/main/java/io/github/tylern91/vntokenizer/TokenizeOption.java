package io.github.tylern91.vntokenizer;

/** Controls how {@link VnTokenizer} treats URL- and host-like substrings during tokenization. */
public enum TokenizeOption {
    /** Segment the entire input as ordinary Vietnamese text. */
    NORMAL,
    /** Keep hostname-shaped substrings as single atomic tokens. */
    HOST,
    /** Keep URL-shaped substrings as single atomic tokens. */
    URL
}
