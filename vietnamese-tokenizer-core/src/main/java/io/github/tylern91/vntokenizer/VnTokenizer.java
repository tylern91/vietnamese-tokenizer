package io.github.tylern91.vntokenizer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class VnTokenizer {

    private static final Path BUNDLED_SENTINEL = Path.of("__bundled__");
    private static final ConcurrentHashMap<Path, VnTokenizer> CACHE = new ConcurrentHashMap<>();

    private VnTokenizer() {}

    public static VnTokenizer getInstance() {
        return CACHE.computeIfAbsent(BUNDLED_SENTINEL, k -> new VnTokenizer());
    }

    public static VnTokenizer getInstance(Path dictDir) {
        return CACHE.computeIfAbsent(dictDir, k -> new VnTokenizer());
    }

    public List<Token> tokenize(String text) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 4");
    }

    public List<Token> tokenize(String text, TokenizeOption option) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 4");
    }

    public List<String> tokenizeToStrings(String text) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 4");
    }

    static void resetInstances() {
        CACHE.clear();
    }
}
