package io.github.tylern91.vntokenizer.internal.dict;

import io.github.tylern91.vntokenizer.internal.score.BigramScores;
import io.github.tylern91.vntokenizer.internal.trie.DoubleArrayTrieBuilder;
import io.github.tylern91.vntokenizer.internal.trie.SyllableTrie;
import io.github.tylern91.vntokenizer.internal.trie.WordTrie;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Loads {@link Dictionaries} either from the bundled classpath resources
 * (dicts jar) or from an external directory of plain-text files.
 */
public final class DictLoader {

    private static final String RESOURCE_DIR = "io/github/tylern91/vntokenizer/dicts/";

    private DictLoader() {
    }

    public static Dictionaries load() {
        try (BufferedReader words = bundledReader("words.txt.gz");
                BufferedReader syllables = bundledReader("syllables.txt.gz");
                BufferedReader bigrams = bundledReader("bigrams.txt.gz")) {
            return build(words, syllables, bigrams);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Dictionaries load(Path dictDir) {
        try (BufferedReader words = Files.newBufferedReader(dictDir.resolve("words.txt"), StandardCharsets.UTF_8);
                BufferedReader syllables = Files.newBufferedReader(dictDir.resolve("syllables.txt"),
                        StandardCharsets.UTF_8);
                BufferedReader bigrams = Files.newBufferedReader(dictDir.resolve("bigrams.txt"),
                        StandardCharsets.UTF_8)) {
            return build(words, syllables, bigrams);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedReader bundledReader(String name) throws IOException {
        InputStream raw = DictLoader.class.getClassLoader().getResourceAsStream(RESOURCE_DIR + name);
        if (raw == null) {
            throw new IOException("Missing bundled resource: " + name);
        }
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
    }

    private static Dictionaries build(BufferedReader wordsReader, BufferedReader syllablesReader,
            BufferedReader bigramsReader) throws IOException {
        List<String> words = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        Map<String, Integer> wordIndex = new HashMap<>();
        String line;
        while ((line = wordsReader.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (tab < 0) {
                throw new IllegalStateException("Malformed word line (expected \"word\\tcount\"): " + line);
            }
            wordIndex.put(line.substring(0, tab), words.size());
            words.add(line.substring(0, tab));
            counts.add(parseCount(line, line.substring(tab + 1)));
        }

        List<String> syllables = new ArrayList<>();
        while ((line = syllablesReader.readLine()) != null) {
            syllables.add(line);
        }

        long total = 0L;
        for (long count : counts) {
            total += count;
        }
        float[] unigramLogProb = new float[words.size()];
        for (int i = 0; i < words.size(); i++) {
            unigramLogProb[i] = (float) Math.log(counts.get(i) / (double) total);
        }

        Map<Long, Float> bigramScores = new HashMap<>();
        while ((line = bigramsReader.readLine()) != null) {
            int firstTab = line.indexOf('\t');
            int secondTab = firstTab < 0 ? -1 : line.indexOf('\t', firstTab + 1);
            if (firstTab < 0 || secondTab < 0) {
                throw new IllegalStateException("Malformed bigram line (expected \"prev\\tnext\\tcount\"): " + line);
            }
            Integer prevId = wordIndex.get(line.substring(0, firstTab));
            Integer nextId = wordIndex.get(line.substring(firstTab + 1, secondTab));
            long bigramCount = parseCount(line, line.substring(secondTab + 1));
            if (prevId == null || nextId == null) {
                continue;
            }
            double pmi = Math.log(bigramCount / (double) counts.get(prevId)) - unigramLogProb[nextId];
            bigramScores.put(BigramScores.key(prevId, nextId), (float) pmi);
        }

        WordTrie wordTrie = DoubleArrayTrieBuilder.buildWordTrie(words);
        SyllableTrie syllableTrie = DoubleArrayTrieBuilder.buildSyllableTrie(syllables);
        return new Dictionaries(wordTrie, syllableTrie, unigramLogProb, BigramScores.of(bigramScores));
    }

    private static long parseCount(String line, String field) {
        try {
            return Long.parseLong(field);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Malformed count field in line: " + line, e);
        }
    }
}
