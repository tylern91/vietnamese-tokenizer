package io.github.tylern91.vntokenizer.acceptance;

import io.github.tylern91.vntokenizer.VnTokenizer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Shared fixture helpers for the {@code *AcceptanceTest} classes. Not a test class.
 *
 * <p>Fixture dictionaries are written in the on-disk formats {@code DictLoader} reads:
 * {@code words.txt} is {@code word<TAB>count}, {@code syllables.txt} is one syllable per line, and
 * {@code bigrams.txt} is {@code prev<TAB>next<TAB>count}. A word's id is its index in the
 * {@link String#compareTo}-sorted word list.
 */
final class AcceptanceFixtures {

    private AcceptanceFixtures() {
    }

    /**
     * Writes the three dictionary files into {@code dir}. Word lines are sorted by their word
     * field first, because the trie builder rejects unsorted input.
     */
    static Path writeDictDir(Path dir, List<String> wordLines, List<String> syllables, List<String> bigramLines) {
        List<String> sortedWords = wordLines.stream()
                .sorted(Comparator.comparing(line -> line.substring(0, line.indexOf('\t'))))
                .toList();
        try {
            Files.write(dir.resolve("words.txt"), sortedWords, StandardCharsets.UTF_8);
            Files.write(dir.resolve("syllables.txt"), syllables.stream().sorted().toList(), StandardCharsets.UTF_8);
            Files.write(dir.resolve("bigrams.txt"), bigramLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    /**
     * Writes a fixture dictionary into {@code dir} and returns the tokenizer cached for it. Each
     * test must pass its own {@code dir} ({@code @TempDir}) so it gets a fresh instance.
     */
    static VnTokenizer tokenizerFor(Path dir, List<String> wordLines, List<String> syllables,
            List<String> bigramLines) {
        return VnTokenizer.getInstance(writeDictDir(dir, wordLines, syllables, bigramLines));
    }
}
