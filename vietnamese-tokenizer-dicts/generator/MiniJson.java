import java.util.HashMap;
import java.util.Map;

/**
 * Minimal, dependency-free parser for the flat JSON objects in UVW-2026's
 * data/train.jsonl. Each line is a single flat JSON object whose values are
 * either JSON strings or JSON numbers (no nested objects/arrays) -- verified
 * against the actual data before writing this: {id, title, content,
 * num_chars, num_sentences, quality}.
 *
 * This is a one-off ETL helper, not a general JSON library: it implements
 * just enough of RFC 8259 to correctly decode string escapes (quote,
 * backslash, slash, backspace/formfeed/newline/CR/tab, and 4-hex-digit
 * backslash-u escapes incl. surrogate pairs) and integer/decimal number
 * literals, for a single top-level object per line.
 */
final class MiniJson {

    private MiniJson() {
    }

    /** Parses one line into a key -> raw-value map. String values are fully
     *  unescaped; numbers are kept as their raw text (caller parses as needed). */
    static Map<String, String> parseFlatObject(String line) {
        Map<String, String> result = new HashMap<>(8);
        int len = line.length();
        int i = skipWs(line, 0, len);
        if (i >= len || line.charAt(i) != '{') {
            throw new IllegalArgumentException("expected '{' at start of line");
        }
        i++;
        i = skipWs(line, i, len);
        if (i < len && line.charAt(i) == '}') {
            return result; // empty object
        }
        while (true) {
            i = skipWs(line, i, len);
            if (i >= len || line.charAt(i) != '"') {
                throw new IllegalArgumentException("expected key string at pos " + i);
            }
            int[] endPos = new int[1];
            String key = parseString(line, i, endPos);
            i = endPos[0];
            i = skipWs(line, i, len);
            if (i >= len || line.charAt(i) != ':') {
                throw new IllegalArgumentException("expected ':' at pos " + i);
            }
            i++;
            i = skipWs(line, i, len);
            String value;
            if (i < len && line.charAt(i) == '"') {
                value = parseString(line, i, endPos);
                i = endPos[0];
            } else {
                // number, boolean, or null literal -- copy raw text through
                int start = i;
                while (i < len && ",}".indexOf(line.charAt(i)) < 0) {
                    i++;
                }
                value = line.substring(start, i).trim();
            }
            result.put(key, value);
            i = skipWs(line, i, len);
            if (i < len && line.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < len && line.charAt(i) == '}') {
                break;
            }
            throw new IllegalArgumentException("expected ',' or '}' at pos " + i);
        }
        return result;
    }

    private static int skipWs(String s, int i, int len) {
        while (i < len) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                break;
            }
            i++;
        }
        return i;
    }

    /** Parses a JSON string starting at the opening quote (s.charAt(startQuote) == '"').
     *  Writes the index just past the closing quote into endPosOut[0]. */
    private static String parseString(String s, int startQuote, int[] endPosOut) {
        int len = s.length();
        int i = startQuote + 1;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= len) {
                throw new IllegalArgumentException("unterminated string");
            }
            char c = s.charAt(i);
            if (c == '"') {
                i++;
                break;
            }
            if (c == '\\') {
                i++;
                if (i >= len) {
                    throw new IllegalArgumentException("dangling escape");
                }
                char esc = s.charAt(i);
                switch (esc) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '/': sb.append('/'); i++; break;
                    case 'b': sb.append('\b'); i++; break;
                    case 'f': sb.append('\f'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 4 >= len) {
                            throw new IllegalArgumentException("truncated \\u escape");
                        }
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 5;
                        break;
                    default:
                        throw new IllegalArgumentException("invalid escape \\" + esc);
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        endPosOut[0] = i;
        return sb.toString();
    }
}
