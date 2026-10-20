package com.hinglishpdf.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranslationValidatorService {

    private static final Logger log = LoggerFactory.getLogger(TranslationValidatorService.class);

    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\b\\d+([,.]\\d+)?\\b");

    private static final Pattern ENGLISH_NEGATION_PATTERN = Pattern.compile(
            "\\b(not|never|no|neither|nor|without|hardly|barely|cannot|can't|didn't|doesn't|wasn't|weren't|won't)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HINDI_NEGATION_PATTERN = Pattern.compile(
            "\\b(nahi|nahin|mat|na|bina|kabhi\\s+nahi)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DANGLING_CONNECTOR_PATTERN = Pattern.compile(
            "(?i)\\s+(aur|lekin|magar|kyunki|ki|ya|ke|par)\\s*[.!?]?$"
    );

    private static final Pattern CHARACTER_STUTTER_PATTERN = Pattern.compile(
            "(?i)\\b[a-z]*([a-z])\\1{3,}[a-z]*\\b"
    );

    private static final Pattern DEVANAGARI_SCRIPT_PATTERN = Pattern.compile(
            "[\\u0900-\\u097F]"
    );

    private static final Set<String> ENGLISH_FUNCTION_WORDS = Set.of(
            "the", "was", "were", "is", "are", "have", "had", "has", "been", "would",
            "could", "should", "with", "from", "that", "this", "which", "because",
            "although", "between", "under", "about", "their", "there", "they", "them",
            "into", "onto", "then", "when", "while", "where", "after", "before",
            "without", "of", "in", "on", "at", "by", "for", "to", "and", "or",
            "an", "a", "all", "any", "no", "not", "we", "he", "she", "it", "i"
    );

    private static final Set<String> HINDI_GRAMMAR_MARKERS = Set.of(
            "hai", "hain", "tha", "thi", "kar", "raha", "rahi", "rahe",
            "karta", "karti", "karte", "hoga", "hogi", "hoge", "gaya", "gayi",
            "gaye", "kiye", "kiya", "diya", "diye", "aur", "lekin", "magar",
            "kyunki", "ki", "ko", "se", "mein", "ka", "ke", "par", "meri", "mera", "mere",
            "uska", "uske", "uski", "unka", "unke", "unki", "apna", "apne", "apni",
            "kuch", "sab", "yeh", "woh", "unhone", "usne", "maine", "humne", "hum", "aap", "tum"
    );

    private static final Map<String, Integer> NUMBER_WORDS = new HashMap<>();

    static {
        NUMBER_WORDS.put("zero", 0);
        NUMBER_WORDS.put("one", 1);
        NUMBER_WORDS.put("two", 2);
        NUMBER_WORDS.put("three", 3);
        NUMBER_WORDS.put("four", 4);
        NUMBER_WORDS.put("five", 5);
        NUMBER_WORDS.put("six", 6);
        NUMBER_WORDS.put("seven", 7);
        NUMBER_WORDS.put("eight", 8);
        NUMBER_WORDS.put("nine", 9);
        NUMBER_WORDS.put("ten", 10);
        NUMBER_WORDS.put("eleven", 11);
        NUMBER_WORDS.put("twelve", 12);
        NUMBER_WORDS.put("thirteen", 13);
        NUMBER_WORDS.put("fourteen", 14);
        NUMBER_WORDS.put("fifteen", 15);
        NUMBER_WORDS.put("sixteen", 16);
        NUMBER_WORDS.put("seventeen", 17);
        NUMBER_WORDS.put("eighteen", 18);
        NUMBER_WORDS.put("nineteen", 19);
        NUMBER_WORDS.put("twenty", 20);
        NUMBER_WORDS.put("thirty", 30);
        NUMBER_WORDS.put("forty", 40);
        NUMBER_WORDS.put("fifty", 50);
        NUMBER_WORDS.put("sixty", 60);
        NUMBER_WORDS.put("seventy", 70);
        NUMBER_WORDS.put("eighty", 80);
        NUMBER_WORDS.put("ninety", 90);
        NUMBER_WORDS.put("hundred", 100);
        NUMBER_WORDS.put("thousand", 1000);
    }

    public record ValidationReport(
            boolean isValid,
            boolean hasSevereIssues,
            List<String> issues,
            int sourceNumbersCount,
            int targetNumbersCount,
            int sourceSentencesCount,
            int targetSentencesCount,
            List<String> untranslatedEnglishSentences,
            List<String> brokenSentences,
            List<String> corruptedTokens
    ) {}

    /**
     * Cleans common prompt leakages, excessive character stuttering, and dangling connectors.
     */
    public String cleanAndNormalize(String translated) {
        if (translated == null || translated.isBlank()) {
            return translated;
        }

        String cleaned = translated.trim();

        // 1. Remove introductory prompt boilerplate
        cleaned = cleaned.replaceAll("(?i)^(Here is the (most natural spoken )?Hinglish translation:?\\s*)+", "").trim();
        cleaned = cleaned.replaceAll("(?i)^(Sure, here is the Hinglish translation:?\\s*)+", "").trim();
        cleaned = cleaned.replaceAll("^```(?:markdown|html)?\\s*", "").replaceAll("\\s*```$", "").trim();

        // 2. Remove surrounding quote wrappers
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("“") && cleaned.endsWith("”"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // 3. Remove accidental character stuttering (e.g. 'sooooo' -> 'so', 'pleeease' -> 'please')
        Matcher stutterMatcher = CHARACTER_STUTTER_PATTERN.matcher(cleaned);
        StringBuilder sbStutter = new StringBuilder();
        while (stutterMatcher.find()) {
            String word = stutterMatcher.group();
            String fixedWord = word.replaceAll("([a-zA-Z])\\1{2,}", "$1");
            stutterMatcher.appendReplacement(sbStutter, Matcher.quoteReplacement(fixedWord));
        }
        stutterMatcher.appendTail(sbStutter);
        cleaned = sbStutter.toString();

        // 4. Clean dangling connector at the end of the entire chunk
        Matcher danglingMatcher = DANGLING_CONNECTOR_PATTERN.matcher(cleaned);
        if (danglingMatcher.find()) {
            cleaned = danglingMatcher.replaceAll(".").trim();
        }

        return cleaned;
    }

    /**
     * Validates translated Hinglish against original English text for:
     * 1. Number preservation (e.g. 'thirty-four' -> 34, years, percentages)
     * 2. Meaning & sentence preservation (omission check, polarity check)
     * 3. Mixed-language validation (untranslated English sentences)
     * 4. Broken sentences & corrupted tokens
     */
    public ValidationReport validate(String sourceText, String translatedHinglish) {
        List<String> issues = new ArrayList<>();
        boolean severe = false;

        if (sourceText == null || sourceText.isBlank()) {
            return new ValidationReport(true, false, Collections.emptyList(), 0, 0, 0, 0,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        if (translatedHinglish == null || translatedHinglish.isBlank()) {
            return new ValidationReport(false, true, List.of("Translated text is completely empty"), 0, 0, 0, 0,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        // --- 1. NUMBER PRESERVATION ---
        Set<String> sourceNumbers = extractAllNumbers(sourceText);
        Set<String> targetNumbers = extractAllNumbers(translatedHinglish);

        int missingNumbers = 0;
        for (String num : sourceNumbers) {
            // Check if the number (e.g. "34") exists in target numbers or anywhere in target text
            if (!targetNumbers.contains(num) && !translatedHinglish.contains(num)) {
                missingNumbers++;
                issues.add("Number preservation mismatch: '" + num + "' was in source but not found in target.");
            }
        }

        // --- 2. MEANING & SENTENCE PRESERVATION ---
        List<String> sourceSentences = splitSentences(sourceText);
        List<String> targetSentences = splitSentences(translatedHinglish);

        if (sourceSentences.size() >= 3 && targetSentences.size() < (sourceSentences.size() * 0.5)) {
            severe = true;
            issues.add("Sentence omission detected: source has " + sourceSentences.size() +
                    " sentences, but target only has " + targetSentences.size() + " sentences.");
        }

        // Polarity / Negation check
        boolean sourceHasNegation = ENGLISH_NEGATION_PATTERN.matcher(sourceText).find();
        boolean targetHasNegation = HINDI_NEGATION_PATTERN.matcher(translatedHinglish).find();
        if (sourceHasNegation && !targetHasNegation && sourceSentences.size() <= 3) {
            issues.add("Potential polarity inversion: source contains negation, but target contains no Hindi negation markers.");
        }

        // --- 3. MIXED-LANGUAGE VALIDATION: UNTRANSLATED ENGLISH SENTENCES ---
        List<String> untranslatedEnglishSentences = new ArrayList<>();
        for (String sentence : targetSentences) {
            if (isUntranslatedEnglishSentence(sentence)) {
                untranslatedEnglishSentences.add(sentence);
                severe = true;
                issues.add("Untranslated English sentence detected: '" + sentence + "'");
            }
        }

        // --- 4. BROKEN SENTENCES & CORRUPTED WORDS ---
        List<String> brokenSentences = new ArrayList<>();
        for (String sentence : targetSentences) {
            String trimmed = sentence.trim();
            if (DANGLING_CONNECTOR_PATTERN.matcher(trimmed).find()) {
                brokenSentences.add(trimmed);
                issues.add("Broken sentence with hanging connector: '" + trimmed + "'");
            }
        }

        List<String> corruptedTokens = new ArrayList<>();
        Matcher stutterMatcher = CHARACTER_STUTTER_PATTERN.matcher(translatedHinglish);
        while (stutterMatcher.find()) {
            corruptedTokens.add(stutterMatcher.group());
            issues.add("Corrupted stuttered token: '" + stutterMatcher.group() + "'");
        }

        if (DEVANAGARI_SCRIPT_PATTERN.matcher(translatedHinglish).find()) {
            corruptedTokens.add("Devanagari script");
            issues.add("Devanagari Unicode script detected (must be Roman/Latin Hinglish).");
        }

        boolean isValid = issues.isEmpty();
        if (!isValid) {
            log.warn("Translation quality issues detected: count={}, severe={}", issues.size(), severe);
        }

        return new ValidationReport(
                isValid,
                severe,
                issues,
                sourceNumbers.size(),
                targetNumbers.size(),
                sourceSentences.size(),
                targetSentences.size(),
                untranslatedEnglishSentences,
                brokenSentences,
                corruptedTokens
        );
    }

    /**
     * Extracts all numerical values, converting spelled-out numbers like
     * 'thirty-four' -> '34', 'twenty-six' -> '26', 'one hundred' -> '100'.
     */
    public Set<String> extractAllNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null || text.isBlank()) return numbers;

        // 1. Match digits (e.g. 34, 1982, 3.5)
        Matcher digitMatcher = DIGITS_PATTERN.matcher(text);
        while (digitMatcher.find()) {
            numbers.add(digitMatcher.group().replace(",", ""));
        }

        // 2. Match spelled-out compound numbers (e.g. thirty-four, twenty-six)
        String remainingText = text.toLowerCase();
        Pattern compoundPattern = Pattern.compile("\\b([a-zA-Z]+)-([a-zA-Z]+)\\b");
        Matcher compoundMatcher = compoundPattern.matcher(remainingText);
        StringBuilder sb = new StringBuilder();
        while (compoundMatcher.find()) {
            String w1 = compoundMatcher.group(1);
            String w2 = compoundMatcher.group(2);
            if (NUMBER_WORDS.containsKey(w1) && NUMBER_WORDS.containsKey(w2)) {
                int val = NUMBER_WORDS.get(w1) + NUMBER_WORDS.get(w2);
                numbers.add(String.valueOf(val));
                compoundMatcher.appendReplacement(sb, " ");
            }
        }
        compoundMatcher.appendTail(sb);
        remainingText = sb.toString();

        // 3. Match single word numbers (e.g. 'thirty', 'fourteen', 'hundred')
        String[] words = remainingText.split("[^a-zA-Z]+");
        for (String w : words) {
            if (NUMBER_WORDS.containsKey(w)) {
                numbers.add(String.valueOf(NUMBER_WORDS.get(w)));
            }
        }

        return numbers;
    }

    private boolean isUntranslatedEnglishSentence(String sentence) {
        String[] tokens = sentence.toLowerCase().split("[^a-zA-Z]+");
        if (tokens.length < 4) {
            return false;
        }

        int englishFuncWordCount = 0;
        int hindiGrammarCount = 0;

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (ENGLISH_FUNCTION_WORDS.contains(token)) {
                englishFuncWordCount++;
            }
            if (HINDI_GRAMMAR_MARKERS.contains(token)) {
                hindiGrammarCount++;
            }
        }

        // If sentence has >= 2 English grammatical function words and ZERO Hindi grammar markers,
        // it was left completely in English!
        return englishFuncWordCount >= 2 && hindiGrammarCount == 0;
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        String[] parts = text.split("(?<=[.!?])\\s+");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }
}
