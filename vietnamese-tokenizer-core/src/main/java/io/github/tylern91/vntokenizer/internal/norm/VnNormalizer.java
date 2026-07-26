package io.github.tylern91.vntokenizer.internal.norm;

import java.text.Normalizer;
import java.util.Arrays;

/**
 * Merges NFD-decomposed Vietnamese vowel sequences (a base or "hat" letter
 * followed by 0-2 trailing combining marks) into their NFC precomposed form.
 * Operates on {@code int[]} codepoints to avoid String/Normalizer allocation
 * on the tokenizer's hot path.
 *
 * <p>Only the 24 base "row" codepoints below are hand-transcribed (and
 * web-verified against Unicode references); the 120 tone-combined forms are
 * derived once, at class-init, via {@link Normalizer} itself — so the JDK's
 * own Unicode data is the source of truth for every precomposed value.
 */
public final class VnNormalizer {

    // Combining marks that stack a "hat" onto a base vowel (â, ă, ê, ô, ơ, ư).
    private static final int MARK_CIRCUMFLEX = 0x0302;
    private static final int MARK_BREVE = 0x0306;
    private static final int MARK_HORN = 0x031B;

    // Combining tone marks (huyền, sắc, hỏi, ngã, nặng).
    private static final int MARK_GRAVE = 0x0300;
    private static final int MARK_ACUTE = 0x0301;
    private static final int MARK_HOOK_ABOVE = 0x0309;
    private static final int MARK_TILDE = 0x0303;
    private static final int MARK_DOT_BELOW = 0x0323;

    // The 24 vowel "rows" — 12 letters (6 plain + 6 already hat-marked) x 2 case.
    private static final int[] ROW_LETTERS = {
        'a', 0x0103, 0x00E2, 'e', 0x00EA, 'i', 'o', 0x00F4, 0x01A1, 'u', 0x01B0, 'y',
        'A', 0x0102, 0x00C2, 'E', 0x00CA, 'I', 'O', 0x00D4, 0x01A0, 'U', 0x01AF, 'Y',
    };

    private static final int ROW_A = 0, ROW_A_BREVE = 1, ROW_A_CIRC = 2,
            ROW_E = 3, ROW_E_CIRC = 4, ROW_I = 5,
            ROW_O = 6, ROW_O_CIRC = 7, ROW_O_HORN = 8,
            ROW_U = 9, ROW_U_HORN = 10, ROW_Y = 11;
    private static final int CASE_OFFSET = 12;

    private static final int[] LETTER_TO_ROW = buildLetterToRow();
    private static final int[][] HAT_ROW_MAP = buildHatRowMap();
    private static final int[][] TONE_FORMS = buildToneForms();

    private VnNormalizer() {}

    public static int[] normalize(int[] codepoints) {
        int[] out = new int[codepoints.length];
        int outLen = 0;
        int i = 0;
        while (i < codepoints.length) {
            int row = rowOf(codepoints[i]);
            if (row < 0) {
                out[outLen++] = codepoints[i];
                i++;
                continue;
            }

            int currentRow = row;
            int consumed = 1;
            int toneIdx = 0;

            int hatIdx = (i + 1 < codepoints.length) ? hatMarkIndex(codepoints[i + 1]) : 0;
            if (hatIdx > 0) {
                // Usual order: hat mark (circumflex/breve/horn), then optional tone mark.
                int newRow = HAT_ROW_MAP[row][hatIdx];
                if (newRow != row) {
                    currentRow = newRow;
                    consumed = 2;
                    if (i + 2 < codepoints.length) {
                        toneIdx = toneMarkIndex(codepoints[i + 2]);
                        if (toneIdx > 0) {
                            consumed = 3;
                        }
                    }
                }
            } else {
                int firstToneIdx = (i + 1 < codepoints.length) ? toneMarkIndex(codepoints[i + 1]) : 0;
                if (firstToneIdx > 0) {
                    // Canonical NFD ordering sorts by combining class: dot-below (220) sorts
                    // before circumflex/breve (230), so "ệ" decomposes as e + dot-below + circumflex
                    // — reversed from the usual hat-then-tone order. Horn (216) is unaffected.
                    consumed = 2;
                    toneIdx = firstToneIdx;
                    if (toneIdx == 5 && i + 2 < codepoints.length) {
                        int reversedHatIdx = hatMarkIndex(codepoints[i + 2]);
                        if (reversedHatIdx > 0) {
                            int newRow = HAT_ROW_MAP[row][reversedHatIdx];
                            if (newRow != row) {
                                currentRow = newRow;
                                consumed = 3;
                            }
                        }
                    }
                }
            }

            out[outLen++] = TONE_FORMS[currentRow][toneIdx];
            i += consumed;
        }
        return Arrays.copyOf(out, outLen);
    }

    private static int rowOf(int codepoint) {
        if (codepoint < 0 || codepoint >= LETTER_TO_ROW.length) {
            return -1;
        }
        return LETTER_TO_ROW[codepoint];
    }

    private static int hatMarkIndex(int codepoint) {
        if (codepoint == MARK_CIRCUMFLEX) return 1;
        if (codepoint == MARK_BREVE) return 2;
        if (codepoint == MARK_HORN) return 3;
        return 0;
    }

    private static int toneMarkIndex(int codepoint) {
        if (codepoint == MARK_GRAVE) return 1;
        if (codepoint == MARK_ACUTE) return 2;
        if (codepoint == MARK_HOOK_ABOVE) return 3;
        if (codepoint == MARK_TILDE) return 4;
        if (codepoint == MARK_DOT_BELOW) return 5;
        return 0;
    }

    private static int[] buildLetterToRow() {
        int max = 0;
        for (int cp : ROW_LETTERS) {
            max = Math.max(max, cp);
        }
        int[] table = new int[max + 1];
        Arrays.fill(table, -1);
        for (int row = 0; row < ROW_LETTERS.length; row++) {
            table[ROW_LETTERS[row]] = row;
        }
        return table;
    }

    /** Columns: 0 = no hat mark, 1 = circumflex, 2 = breve, 3 = horn. */
    private static int[][] buildHatRowMap() {
        int[][] map = new int[ROW_LETTERS.length][4];
        for (int row = 0; row < ROW_LETTERS.length; row++) {
            Arrays.fill(map[row], row); // default: invalid combo == no-op
        }
        applyHatTransitions(map, 0);
        applyHatTransitions(map, CASE_OFFSET);
        return map;
    }

    private static void applyHatTransitions(int[][] map, int caseOffset) {
        map[ROW_A + caseOffset][1] = ROW_A_CIRC + caseOffset;
        map[ROW_A + caseOffset][2] = ROW_A_BREVE + caseOffset;
        map[ROW_E + caseOffset][1] = ROW_E_CIRC + caseOffset;
        map[ROW_O + caseOffset][1] = ROW_O_CIRC + caseOffset;
        map[ROW_O + caseOffset][3] = ROW_O_HORN + caseOffset;
        map[ROW_U + caseOffset][3] = ROW_U_HORN + caseOffset;
    }

    /** Columns: 0 = no tone, 1 = grave, 2 = acute, 3 = hook above, 4 = tilde, 5 = dot below. */
    private static int[][] buildToneForms() {
        int[] toneMarks = {0, MARK_GRAVE, MARK_ACUTE, MARK_HOOK_ABOVE, MARK_TILDE, MARK_DOT_BELOW};
        int[][] forms = new int[ROW_LETTERS.length][toneMarks.length];
        for (int row = 0; row < ROW_LETTERS.length; row++) {
            forms[row][0] = ROW_LETTERS[row];
            for (int t = 1; t < toneMarks.length; t++) {
                String decomposed = new String(Character.toChars(ROW_LETTERS[row]))
                        + new String(Character.toChars(toneMarks[t]));
                String composed = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
                forms[row][t] = composed.codePointAt(0);
            }
        }
        return forms;
    }
}
