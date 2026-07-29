import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * One-off offline ETL generator for the vietnamese-tokenizer-dicts compiled
 * resources. See vietnamese-tokenizer-dicts/generator/README (this header)
 * for pipeline design notes and rationale.
 *
 * Inputs (already downloaded, verified, gitignored -- see raw-corpus/*\/PROVENANCE.md):
 *   - vietnamese-tokenizer-dicts/raw-corpus/wiktionary/viwiktionary-20260701-pages-articles.xml
 *     (decompressed from the .bz2 dump via `bzip2 -dk`)
 *   - vietnamese-tokenizer-dicts/raw-corpus/uvw2026/data/train.jsonl
 *
 * Outputs (gzip-compressed, written directly into the dicts module's resources):
 *   - words.txt.gz      word\tfreq, sorted lexicographically by word
 *   - syllables.txt.gz  one distinct syllable per line
 *   - bigrams.txt.gz    word1\tword2\tcount
 *   - NOTICE            plain-text attribution (not gzipped)
 *
 * Run (from repo root, after `bzip2 -dk` on the wiktionary dump):
 *   cd vietnamese-tokenizer-dicts/generator
 *   javac -d out MiniJson.java GenerateDicts.java
 *   java -Xmx8g -cp out GenerateDicts
 *
 * Design decisions called out here (see final report for the full list):
 *   - Tokenization for both the UVW-2026 syllable inventory and the greedy
 *     segmentation pass extracts runs of Vietnamese-alphabet letters only
 *     (the 89-letter lowercase set: 12 vowels x 6 tone forms, plus 17
 *     untoned consonants incl. "đ") rather than naive whitespace splitting
 *     or a generic Unicode-letter-run regex. Two problems this fixes vs. a
 *     generic \p{L}+ run: (1) the UVW-2026 `content` field still has
 *     un-stripped MediaWiki/infobox artifacts ("| links =", "__NOTOC__",
 *     etc.) that a generic letter-run would still treat as tokens; (2) since
 *     viwiktionary indexes words from every language, a generic letter-run
 *     let non-Vietnamese scripts (Hangul, Han, Tai Viet, Cyrillic) and
 *     non-Vietnamese Latin diacritics (Turkish, Pinyin romanization, German
 *     umlauts, IAST Sanskrit transliteration, ...) leak into the syllable
 *     inventory -- which in turn let non-Vietnamese Wiktionary titles (e.g.
 *     Korean "남자 친구") survive the step-3 filter. Plain-ASCII foreign
 *     words that happen to use only letters from the Vietnamese subset
 *     (e.g. "a bit", "a cappella") can still slip through -- true language
 *     identification is out of scope for this bootstrap filter.
 *   - quality cutoff: kept lines with quality >= 3 (drops the quality=2 tier,
 *     which is dominated by short stub/navigation pages -- see report for
 *     the full histogram and rationale).
 *   - bigrams.txt.gz counts adjacency only between two *consecutive matched
 *     dictionary-word tokens* in the greedy segmentation (i.e. both sides are
 *     entries that also appear in words.txt). A single leftover syllable
 *     between two dictionary words breaks the bigram chain. Single-syllable
 *     tokens themselves are never counted in either words.txt or bigrams.txt
 *     (they belong to syllables.txt, which by design carries no frequency
 *     data).
 *   - Wiktionary titles are filtered to ns=0 (main/article namespace) pages
 *     only, to exclude Wiktionary:/Thảo luận:/Bản mẫu:/etc. meta pages.
 */
public class GenerateDicts {

    // Resolved relative to the current working directory (handles being run
    // from generator/, the dicts module root, or the repo root).
    private static final Path DICTS_MODULE_DIR = resolveDictsModuleDir();

    private static final Path WIKTIONARY_XML = DICTS_MODULE_DIR.resolve(
            "raw-corpus/wiktionary/viwiktionary-20260701-pages-articles.xml");
    private static final Path UVW_JSONL = DICTS_MODULE_DIR.resolve("raw-corpus/uvw2026/data/train.jsonl");
    private static final Path OUT_DIR = DICTS_MODULE_DIR.resolve(
            "src/main/resources/io/github/tylern91/vntokenizer/dicts");

    /** quality >= this value is kept for the bootstrap segmentation pass.
     *  See report for the full quality histogram; quality=2 is dominated by short
     *  stub/navigation pages (e.g. the wiki front page: 471 chars, 1 sentence). */
    private static final int QUALITY_CUTOFF = 3;

    /** The 89 lowercase letters of the Vietnamese alphabet: 12 vowels (a ă â e ê i o
     *  ô ơ u ư y) x 6 tone forms each (bare + huyền/sắc/hỏi/ngã/nặng), plus 17 untoned
     *  consonants (b c d đ g h k l m n p q r s t v x). Deliberately excludes f/j/w/z
     *  and any non-Vietnamese Latin diacritics (umlauts, macrons, carons, cedillas, ...)
     *  as well as all non-Latin scripts. */
    private static final String VN_LETTERS =
            "aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ"
                    + "bcdđghklmnpqrstvx";
    private static final Pattern LETTER_RUN = Pattern.compile("[" + VN_LETTERS + "]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Vowel letters only (the tone-form vowel half of {@link #VN_LETTERS}), used by the
     *  syllable-nucleus check in {@link #isPlausibleSyllable(String)}. */
    private static final String VOWELS =
            "aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ";

    /** Valid Vietnamese syllable onsets (initial consonant sounds), longest-first grouping
     *  doesn't matter here since {@link #isPlausibleSyllable(String)} tries every length. */
    private static final Set<String> VALID_ONSETS = Set.of(
            "ngh", "ch", "gh", "gi", "kh", "ng", "nh", "ph", "qu", "th", "tr",
            "b", "c", "d", "đ", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x");

    /** Valid Vietnamese syllable codas (finals); semivowel offglides (i/y/o/u) are treated
     *  as part of the vowel nucleus instead of as a separate coda. */
    private static final Set<String> VALID_CODAS = Set.of("ch", "ng", "nh", "c", "m", "n", "p", "t");

    /** Valid vowel-nucleus skeletons (tone marks stripped via {@link #baseVowel(char)}) --
     *  monophthongs plus the closed set of Vietnamese diphthongs/triphthongs. A bare run of
     *  vowel *characters* isn't sufficient (e.g. "aa", "aae" use only vowel letters but are
     *  not real Vietnamese nuclei), so this whitelist checks the actual vowel skeleton. */
    private static final Set<String> VALID_NUCLEI = Set.of(
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
            "ai", "ao", "au", "ay", "âu", "ây", "eo", "êu", "ia", "iê", "iu",
            "oa", "oă", "oe", "oi", "oo", "ôi", "ôô", "ơi",
            "ua", "uâ", "uê", "ui", "uô", "uơ", "uy", "ưa", "ươ", "ưi", "ưu", "yê",
            "iêu", "oai", "oay", "oeo", "uây", "uôi", "ươi", "ươu", "uya", "uyê", "uyu", "yêu");

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        log("dicts module dir: " + DICTS_MODULE_DIR);
        log("wiktionary xml:   " + WIKTIONARY_XML + " exists=" + Files.exists(WIKTIONARY_XML));
        log("uvw jsonl:        " + UVW_JSONL + " exists=" + Files.exists(UVW_JSONL));
        log("output dir:       " + OUT_DIR + " exists=" + Files.exists(OUT_DIR));
        Files.createDirectories(OUT_DIR);

        // Wiktionary multi-syllable title candidates (ns=0 only).
        long t1 = System.currentTimeMillis();
        Set<String> wiktionaryWords = parseWiktionaryTitles(WIKTIONARY_XML);
        long t2 = System.currentTimeMillis();
        log("wiktionary titles: " + wiktionaryWords.size() + " multi-syllable candidates in "
                + (t2 - t1) + " ms");

        // Syllable inventory from ALL uvw2026 lines (title + content), unfiltered by quality.
        Set<String> syllables = buildSyllableInventory(UVW_JSONL);
        long t3 = System.currentTimeMillis();
        log("syllable inventory: " + syllables.size() + " distinct syllables in " + (t3 - t2) + " ms");

        // Filter wiktionary candidates down to those whose every syllable is attested.
        Set<String> filteredWords = filterWords(wiktionaryWords, syllables);
        int maxSyllables = maxSyllableCount(filteredWords);
        long t4 = System.currentTimeMillis();
        log("filtered word list: " + filteredWords.size() + " words (maxSyllables=" + maxSyllables
                + ") in " + (t4 - t3) + " ms");

        // Bootstrap greedy longest-match segmentation over quality-filtered lines.
        SegmentationResult seg = runSegmentation(UVW_JSONL, filteredWords, maxSyllables, QUALITY_CUTOFF);
        long t5 = System.currentTimeMillis();
        log("segmentation: kept " + seg.linesKept + "/" + seg.linesTotal + " lines, "
                + seg.unigram.size() + " distinct words counted, " + seg.bigram.size()
                + " distinct bigrams, in " + (t5 - t4) + " ms");

        // Assemble + write output files.
        TreeMap<String, Integer> finalWords = new TreeMap<>();
        int flooredCount = 0;
        for (String w : filteredWords) {
            Integer freq = seg.unigram.get(w);
            if (freq == null) {
                freq = 1; // frequency floor for words never observed during bootstrap segmentation
                flooredCount++;
            }
            finalWords.put(w, freq);
        }
        log("words never observed during segmentation (frequency floored to 1): " + flooredCount);

        Path wordsOut = OUT_DIR.resolve("words.txt.gz");
        Path syllablesOut = OUT_DIR.resolve("syllables.txt.gz");
        Path bigramsOut = OUT_DIR.resolve("bigrams.txt.gz");
        Path noticeOut = OUT_DIR.resolve("NOTICE");

        writeWords(wordsOut, finalWords);
        writeSyllables(syllablesOut, syllables);
        writeBigrams(bigramsOut, seg.bigram);
        writeNotice(noticeOut);

        long t6 = System.currentTimeMillis();
        log("write: " + (t6 - t5) + " ms");
        log("TOTAL wall clock: " + (t6 - t0) + " ms (" + String.format(Locale.ROOT, "%.1f", (t6 - t0) / 1000.0)
                + " s)");

        log("output sizes:");
        for (Path p : List.of(wordsOut, syllablesOut, bigramsOut, noticeOut)) {
            log("  " + p.getFileName() + ": " + Files.size(p) + " bytes");
        }

        // Spot checks for known compound words.
        log("spot check (present in finalWords?):");
        for (String probe : List.of("hà nội", "việt nam", "học sinh", "xin chào")) {
            Integer freq = finalWords.get(probe);
            log("  \"" + probe + "\" -> " + (freq == null ? "MISSING" : ("freq=" + freq)));
        }
    }

    // ---------------------------------------------------------------
    // Wiktionary XML -> multi-syllable title candidates
    // ---------------------------------------------------------------

    private static Set<String> parseWiktionaryTitles(Path xmlPath) throws Exception {
        Set<String> result = new HashSet<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = new BufferedInputStream(new FileInputStream(xmlPath.toFile()), 1 << 20)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            boolean insidePage = false;
            String currentTitle = null;
            String currentNs = null;
            long pageCount = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if (local.equals("page")) {
                        insidePage = true;
                        currentTitle = null;
                        currentNs = null;
                    } else if (insidePage && local.equals("title") && currentTitle == null) {
                        currentTitle = reader.getElementText();
                    } else if (insidePage && local.equals("ns") && currentNs == null) {
                        currentNs = reader.getElementText();
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (reader.getLocalName().equals("page")) {
                        insidePage = false;
                        pageCount++;
                        if (pageCount % 200_000 == 0) {
                            log("  ...parsed " + pageCount + " wiktionary pages");
                        }
                        if ("0".equals(currentNs) && currentTitle != null) {
                            String candidate = titleToCandidate(currentTitle);
                            if (candidate != null) {
                                result.add(candidate);
                            }
                        }
                    }
                }
            }
            reader.close();
            log("  total wiktionary pages scanned: " + pageCount);
        }
        return result;
    }

    /** Returns the normalized multi-syllable candidate word, or null if the title
     *  is single-syllable (no internal whitespace) after trimming. */
    private static String titleToCandidate(String rawTitle) {
        String trimmed = rawTitle.trim();
        if (!WHITESPACE_ANYWHERE.matcher(trimmed).find()) {
            return null; // single token -- handled by the syllable-fallback path, not this word list
        }
        String normalized = normalize(trimmed);
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        if (normalized.isEmpty() || !normalized.contains(" ")) {
            return null;
        }
        return normalized;
    }

    private static final Pattern WHITESPACE_ANYWHERE = Pattern.compile("\\s");

    // ---------------------------------------------------------------
    // Syllable inventory from ALL uvw2026 lines
    // ---------------------------------------------------------------

    private static Set<String> buildSyllableInventory(Path jsonlPath) throws IOException {
        Set<String> syllables = new HashSet<>(1 << 20);
        long n = 0;
        try (BufferedReader br = openUtf8(jsonlPath)) {
            String line;
            while ((line = br.readLine()) != null) {
                n++;
                if (line.isEmpty()) {
                    continue;
                }
                Map<String, String> obj = MiniJson.parseFlatObject(line);
                addTokens(syllables, obj.get("title"));
                addTokens(syllables, obj.get("content"));
                if (n % 100_000 == 0) {
                    log("  ...syllable pass read " + n + " lines, " + syllables.size() + " distinct syllables so far");
                }
            }
        }
        log("  total uvw2026 lines read (pass 1): " + n);
        return syllables;
    }

    private static void addTokens(Set<String> sink, String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        for (String tok : tokenizeLetters(normalize(rawText))) {
            if (isPlausibleSyllable(tok)) {
                sink.add(tok);
            }
        }
    }

    /**
     * Approximates Vietnamese syllable phonotactics -- optional onset + a 1-3
     * letter vowel nucleus + optional coda -- to reject non-Vietnamese letter
     * runs that happen to use only the restricted 89-letter alphabet (English
     * words, concatenated URL/domain slugs, etc). Tries every onset/coda length
     * combination rather than a single greedy parse, since only *some* valid
     * decomposition needs to exist for the token to be a plausible syllable.
     * Not a complete grammar (loanwords and rare finals may be missed), but it
     * eliminates the bulk of non-syllable noise from the UVW-2026 corpus.
     */
    private static boolean isPlausibleSyllable(String s) {
        int len = s.length();
        if (len == 0 || len > 8) {
            return false;
        }
        for (int onsetLen = 0; onsetLen <= Math.min(3, len); onsetLen++) {
            if (onsetLen > 0 && !VALID_ONSETS.contains(s.substring(0, onsetLen))) {
                continue;
            }
            for (int codaLen = 0; codaLen <= Math.min(2, len - onsetLen); codaLen++) {
                if (codaLen > 0 && !VALID_CODAS.contains(s.substring(len - codaLen))) {
                    continue;
                }
                int nucleusLen = len - onsetLen - codaLen;
                if (nucleusLen < 1 || nucleusLen > 3) {
                    continue;
                }
                if (isValidNucleus(s.substring(onsetLen, onsetLen + nucleusLen))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Strips the tone mark from a vowel character, returning its toneless base form,
     *  or {@code null} if {@code c} isn't a vowel at all. Each base vowel occupies a
     *  contiguous run of 6 tone variants (none, grave, acute, hook, tilde, dot-below)
     *  within {@link #VOWELS}. */
    private static Character baseVowel(char c) {
        int idx = VOWELS.indexOf(c);
        if (idx < 0) {
            return null;
        }
        return VOWELS.charAt((idx / 6) * 6);
    }

    private static boolean isValidNucleus(String s) {
        StringBuilder base = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            Character b = baseVowel(s.charAt(i));
            if (b == null) {
                return false;
            }
            base.append(b.charValue());
        }
        return VALID_NUCLEI.contains(base.toString());
    }

    // ---------------------------------------------------------------
    // Filter
    // ---------------------------------------------------------------

    private static Set<String> filterWords(Set<String> wiktionaryWords, Set<String> syllables) {
        Set<String> filtered = new HashSet<>();
        for (String candidate : wiktionaryWords) {
            String[] parts = WHITESPACE.split(candidate);
            boolean allPresent = true;
            for (String part : parts) {
                if (!syllables.contains(part)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static int maxSyllableCount(Set<String> words) {
        int max = 2;
        for (String w : words) {
            int count = 1;
            for (int i = 0; i < w.length(); i++) {
                if (w.charAt(i) == ' ') {
                    count++;
                }
            }
            if (count > max) {
                max = count;
            }
        }
        return max;
    }

    // ---------------------------------------------------------------
    // Bootstrap greedy longest-match segmentation
    // ---------------------------------------------------------------

    private static final class SegmentationResult {
        final Map<String, Integer> unigram = new HashMap<>();
        final Map<String, Integer> bigram = new HashMap<>();
        long linesKept;
        long linesTotal;
    }

    private static SegmentationResult runSegmentation(Path jsonlPath, Set<String> filteredWords, int maxSyllables,
            int qualityCutoff) throws IOException {
        SegmentationResult result = new SegmentationResult();
        try (BufferedReader br = openUtf8(jsonlPath)) {
            String line;
            while ((line = br.readLine()) != null) {
                result.linesTotal++;
                if (line.isEmpty()) {
                    continue;
                }
                Map<String, String> obj = MiniJson.parseFlatObject(line);
                String qualityStr = obj.get("quality");
                int quality = qualityStr == null ? Integer.MIN_VALUE : Integer.parseInt(qualityStr.trim());
                if (quality < qualityCutoff) {
                    continue;
                }
                result.linesKept++;
                String content = obj.get("content");
                if (content == null || content.isEmpty()) {
                    continue;
                }
                List<String> tokens = tokenizeLetters(normalize(content));
                segmentAndTally(tokens, filteredWords, maxSyllables, result.unigram, result.bigram);
                if (result.linesTotal % 100_000 == 0) {
                    log("  ...segmentation pass read " + result.linesTotal + " lines (" + result.linesKept
                            + " kept), " + result.unigram.size() + " distinct words tallied so far");
                }
            }
        }
        return result;
    }

    private static void segmentAndTally(List<String> tokens, Set<String> filteredWords, int maxSyllables,
            Map<String, Integer> unigram, Map<String, Integer> bigram) {
        int n = tokens.size();
        String prevWord = null; // last emitted token, only set when it was a matched dictionary word
        int i = 0;
        StringBuilder sb = new StringBuilder();
        while (i < n) {
            String matched = null;
            int matchedLen = 0;
            int upper = Math.min(maxSyllables, n - i);
            for (int len = upper; len >= 2; len--) {
                sb.setLength(0);
                for (int k = 0; k < len; k++) {
                    if (k > 0) {
                        sb.append(' ');
                    }
                    sb.append(tokens.get(i + k));
                }
                String candidate = sb.toString();
                if (filteredWords.contains(candidate)) {
                    matched = candidate;
                    matchedLen = len;
                    break;
                }
            }
            if (matched != null) {
                unigram.merge(matched, 1, Integer::sum);
                if (prevWord != null) {
                    bigram.merge(prevWord + "\t" + matched, 1, Integer::sum);
                }
                prevWord = matched;
                i += matchedLen;
            } else {
                // unmatched single syllable: breaks the bigram chain, not tallied anywhere
                prevWord = null;
                i += 1;
            }
        }
    }

    // ---------------------------------------------------------------
    // shared helpers
    // ---------------------------------------------------------------

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static List<String> tokenizeLetters(String normalizedText) {
        List<String> out = new ArrayList<>();
        Matcher m = LETTER_RUN.matcher(normalizedText);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static BufferedReader openUtf8(Path p) throws IOException {
        Reader r = new InputStreamReader(new BufferedInputStream(new FileInputStream(p.toFile()), 1 << 20),
                StandardCharsets.UTF_8);
        return new BufferedReader(r, 1 << 20);
    }

    private static Writer gzipWriter(Path p) throws IOException {
        return new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(p.toFile()), 1 << 16) {
                    {
                        def.setLevel(9);
                    }
                },
                StandardCharsets.UTF_8);
    }

    private static void writeWords(Path out, TreeMap<String, Integer> words) throws IOException {
        try (BufferedWriter w = new BufferedWriter(gzipWriter(out), 1 << 20)) {
            for (Map.Entry<String, Integer> e : words.entrySet()) {
                w.write(e.getKey());
                w.write('\t');
                w.write(Integer.toString(e.getValue()));
                w.write('\n');
            }
        }
    }

    private static void writeSyllables(Path out, Set<String> syllables) throws IOException {
        TreeSet<String> sorted = new TreeSet<>(syllables);
        try (BufferedWriter w = new BufferedWriter(gzipWriter(out), 1 << 20)) {
            for (String s : sorted) {
                w.write(s);
                w.write('\n');
            }
        }
    }

    private static void writeBigrams(Path out, Map<String, Integer> bigram) throws IOException {
        TreeMap<String, Integer> sorted = new TreeMap<>(bigram);
        try (BufferedWriter w = new BufferedWriter(gzipWriter(out), 1 << 20)) {
            for (Map.Entry<String, Integer> e : sorted.entrySet()) {
                String[] parts = e.getKey().split("\t", 2);
                w.write(parts[0]);
                w.write('\t');
                w.write(parts[1]);
                w.write('\t');
                w.write(Integer.toString(e.getValue()));
                w.write('\n');
            }
        }
    }

    private static void writeNotice(Path out) throws IOException {
        String notice = ""
                + "NOTICE\n"
                + "======\n"
                + "\n"
                + "This module's compiled dictionary resources (words.txt.gz, syllables.txt.gz,\n"
                + "bigrams.txt.gz) were derived from the two sources below, both licensed under\n"
                + "the Creative Commons Attribution-ShareAlike 4.0 International License\n"
                + "(CC BY-SA 4.0): https://creativecommons.org/licenses/by-sa/4.0/\n"
                + "\n"
                + "1. Vietnamese-language Wiktionary (viwiktionary), Wikimedia Foundation\n"
                + "   Source URL: https://dumps.wikimedia.org/viwiktionary/latest/viwiktionary-latest-pages-articles.xml.bz2\n"
                + "   Resolved dump run: 20260701\n"
                + "     (http://download.wikimedia.org/viwiktionary/20260701/viwiktionary-20260701-pages-articles.xml.bz2,\n"
                + "     pubDate Thu, 02 Jul 2026 18:11:15 GMT)\n"
                + "   License: dual GFDL / CC BY-SA 4.0 (per https://dumps.wikimedia.org/legal.html);\n"
                + "     this project uses it under CC BY-SA 4.0.\n"
                + "   Content used: page titles (main/article namespace, ns=0 only), used as a\n"
                + "     seed candidate list for multi-syllable Vietnamese compound words.\n"
                + "\n"
                + "2. UVW-2026 dataset (undertheseanlp/UVW-2026), Underthesea Vietnamese NLP project\n"
                + "   Source URL: https://huggingface.co/datasets/undertheseanlp/UVW-2026\n"
                + "   Revision (commit): a0a79294e4568137e25828bb3f2a4cde8546e1fb\n"
                + "   License: CC BY-SA 4.0 (per the dataset's README.md front matter\n"
                + "     `license: cc-by-sa-4.0`), consistent with the underlying Wikipedia content\n"
                + "     license it was built from.\n"
                + "   Content used: `data/train.jsonl` title/content text, used to build the\n"
                + "     syllable inventory and, for quality-filtered lines, word/bigram frequency\n"
                + "     counts via bootstrap greedy longest-match segmentation.\n"
                + "\n"
                + "No modifications were made to the licensed text itself beyond standard NLP\n"
                + "preprocessing (Unicode NFC normalization, lowercasing, tokenization, frequency\n"
                + "counting); the derived word/syllable/bigram lists in this directory are\n"
                + "themselves distributed under the same CC BY-SA 4.0 terms as a share-alike\n"
                + "derivative work.\n";
        Files.write(out, notice.getBytes(StandardCharsets.UTF_8));
    }

    private static Path resolveDictsModuleDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        // Walk up looking for a directory literally named vietnamese-tokenizer-dicts
        // that contains raw-corpus/. Handles being run from generator/ (typical) or
        // from the repo root.
        Path candidate = cwd;
        for (int i = 0; i < 6 && candidate != null; i++) {
            Path maybe = candidate.getFileName() != null
                    && candidate.getFileName().toString().equals("vietnamese-tokenizer-dicts")
                            ? candidate
                            : candidate.resolve("vietnamese-tokenizer-dicts");
            if (Files.isDirectory(maybe.resolve("raw-corpus"))) {
                return maybe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate vietnamese-tokenizer-dicts/raw-corpus/ from cwd=" + cwd
                        + " -- run this from the generator/ directory or the repo root.");
    }

    private static void log(String msg) {
        System.err.println("[" + java.time.LocalTime.now().withNano(0) + "] " + msg);
    }
}
