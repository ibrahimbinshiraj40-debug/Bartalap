package org.bartalap.tools;

/**
 * Bartalap: Avro-style phonetic Banglish -> Bangla converter.
 * Pure Java (no Android dependency) so it can be unit tested anywhere.
 *
 * Rules (simplified Avro):
 *  - Longest match first (e.g. "kh" before "k").
 *  - Vowel after a consonant becomes a kar (া ি ী ...), otherwise independent (আ ই ...).
 *  - "o" after a consonant is the inherent vowel (nothing is written).
 *  - Two consonants typed together are joined with hasanta (্) -> juktakkhor.
 *  - "y" / "z" right after a consonant -> jofola (্য).
 *  - Digits -> Bangla digits, "." -> "।" (".." -> ".").
 */
public final class BanglishConverter {

    private BanglishConverter() {}

    private static final String HASANTA = "্";

    // pattern, bangla  (consonants). Case-sensitive patterns first, then lowercase.
    private static final String[][] CONSONANTS = {
            {"Rh", "ঢ়"}, {"Sh", "ষ"}, {"Th", "ঠ"}, {"Dh", "ঢ"}, {"Ng", "ঙ"}, {"NG", "ঞ"}, {"Ch", "ছ"},
            {"chh", "ছ"}, {"kh", "খ"}, {"gh", "ঘ"}, {"ng", "ং"}, {"ch", "চ"}, {"jh", "ঝ"}, {"th", "থ"},
            {"dh", "ধ"}, {"ph", "ফ"}, {"bh", "ভ"}, {"sh", "শ"},
            {"T", "ট"}, {"D", "ড"}, {"N", "ণ"}, {"R", "ড়"}, {"S", "শ"}, {"J", "জ"},
            {"k", "ক"}, {"g", "গ"}, {"c", "চ"}, {"j", "জ"}, {"t", "ত"}, {"d", "দ"}, {"n", "ন"},
            {"p", "প"}, {"f", "ফ"}, {"b", "ব"}, {"v", "ভ"}, {"m", "ম"}, {"z", "য"}, {"y", "য়"},
            {"r", "র"}, {"l", "ল"}, {"s", "স"}, {"h", "হ"}, {"q", "ক"}, {"x", "ক্স"},
            {"K", "ক"}, {"G", "গ"}, {"C", "চ"}, {"P", "প"}, {"F", "ফ"}, {"B", "ব"}, {"V", "ভ"},
            {"M", "ম"}, {"Z", "য"}, {"Y", "য়"}, {"L", "ল"}, {"H", "হ"}, {"Q", "ক"}, {"X", "ক্স"}
    };

    // pattern, independent vowel, kar
    private static final String[][] VOWELS = {
            {"rri", "ঋ", "ৃ"}, {"OI", "ঐ", "ৈ"}, {"OU", "ঔ", "ৌ"}, {"oi", "ঐ", "ৈ"}, {"ou", "ঔ", "ৌ"},
            {"ee", "ঈ", "ী"}, {"oo", "ঊ", "ূ"},
            {"a", "আ", "া"}, {"A", "আ", "া"}, {"i", "ই", "ি"}, {"I", "ঈ", "ী"}, {"u", "উ", "ু"},
            {"U", "ঊ", "ূ"}, {"e", "এ", "ে"}, {"E", "এ", "ে"}, {"O", "ও", "ো"}, {"o", "অ", ""},
            {"w", "ও", "ো"}, {"W", "ও", "ো"}
    };

    /** Consonants after which no hasanta is added when another consonant follows. */
    private static boolean noHasantaAfter(String bn) {
        return bn.equals("ং") || bn.equals("য়") || bn.equals("ড়") || bn.equals("ঢ়") || bn.equals("ক্স");
    }

    public static String convert(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        String prevConsonant = null; // last written consonant (if the previous token was a consonant)
        boolean prevWasVowel = false;
        int i = 0;
        int n = input.length();
        while (i < n) {
            char ch = input.charAt(i);

            // Digits
            if (ch >= '0' && ch <= '9') {
                out.append((char) ('০' + (ch - '0')));
                prevConsonant = null;
                prevWasVowel = false;
                i++;
                continue;
            }
            // Dari
            if (ch == '.') {
                if (i + 1 < n && input.charAt(i + 1) == '.') {
                    out.append('.');
                    i += 2;
                } else {
                    out.append('।');
                    i++;
                }
                prevConsonant = null;
                prevWasVowel = false;
                continue;
            }
            if (ch == '^') { // chandrabindu
                out.append('ঁ');
                i++;
                continue;
            }

            // Vowels
            String[] v = matchVowel(input, i);
            if (v != null) {
                boolean wordEnd = i + v[0].length() >= n || !Character.isLetter(input.charAt(i + v[0].length()));
                if (v[0].equals("o") && prevConsonant != null && wordEnd) {
                    // Casual Banglish: "bhalo" -> ভালো, "acho" -> আচো
                    out.append("ো");
                } else if (v[0].equals("o") && prevConsonant == null && prevWasVowel) {
                    // "dao" -> দাও, "jao" -> জাও
                    out.append("ও");
                } else if (prevConsonant != null) {
                    out.append(v[2]);
                } else {
                    out.append(v[1]);
                }
                prevConsonant = null;
                prevWasVowel = true;
                i += v[0].length();
                continue;
            }

            // Consonants
            String[] c = matchConsonant(input, i);
            if (c != null) {
                String bn = c[1];
                if (prevConsonant != null) {
                    if ((c[0].equals("y") || c[0].equals("z") || c[0].equals("Y") || c[0].equals("Z"))
                            && !noHasantaAfter(prevConsonant)) {
                        out.append(HASANTA).append("য");
                        prevConsonant = "য";
                        prevWasVowel = false;
                        i += c[0].length();
                        continue;
                    }
                    if (!noHasantaAfter(prevConsonant)) {
                        out.append(HASANTA);
                    }
                }
                out.append(bn);
                prevConsonant = bn;
                prevWasVowel = false;
                i += c[0].length();
                continue;
            }

            // Anything else (space, punctuation, already-Bangla text, emoji)
            out.append(ch);
            prevConsonant = null;
            prevWasVowel = false;
            i++;
        }
        return out.toString();
    }

    private static String[] matchVowel(String s, int i) {
        for (String[] v : VOWELS) {
            if (s.startsWith(v[0], i)) return v;
        }
        return null;
    }

    private static String[] matchConsonant(String s, int i) {
        for (String[] c : CONSONANTS) {
            if (s.startsWith(c[0], i)) return c;
        }
        return null;
    }
}
