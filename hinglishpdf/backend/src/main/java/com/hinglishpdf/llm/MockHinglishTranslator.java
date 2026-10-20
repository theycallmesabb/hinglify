package com.hinglishpdf.llm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockHinglishTranslator {

    private static final Map<String, String> WORD_REPLACEMENTS = new LinkedHashMap<>();

    static {
        // Multi-word phrases first
        WORD_REPLACEMENTS.put("this document outlines", "yeh document outline karta hai");
        WORD_REPLACEMENTS.put("as a matter of fact", "asal mein");
        WORD_REPLACEMENTS.put("for the first time", "pehli baar");
        WORD_REPLACEMENTS.put("in the morning", "subah mein");
        WORD_REPLACEMENTS.put("in the evening", "shaam ko");
        WORD_REPLACEMENTS.put("at that time", "us waqt");

        // Single words - strictly whole-word matching
        WORD_REPLACEMENTS.put("if", "agar");
        WORD_REPLACEMENTS.put("when", "jab");
        WORD_REPLACEMENTS.put("because", "kyunki");
        WORD_REPLACEMENTS.put("but", "lekin");
        WORD_REPLACEMENTS.put("so", "isliye");
        WORD_REPLACEMENTS.put("although", "halanki");
        WORD_REPLACEMENTS.put("perhaps", "shayad");
        WORD_REPLACEMENTS.put("always", "hamesha");
        WORD_REPLACEMENTS.put("never", "kabhi nahi");
        WORD_REPLACEMENTS.put("very", "bahut");
        WORD_REPLACEMENTS.put("today", "aaj");
        WORD_REPLACEMENTS.put("tomorrow", "kal");
        WORD_REPLACEMENTS.put("yesterday", "kal");
        WORD_REPLACEMENTS.put("everyone", "sab log");
        WORD_REPLACEMENTS.put("everything", "sab kuch");
        WORD_REPLACEMENTS.put("suddenly", "achanak");
        WORD_REPLACEMENTS.put("slowly", "dheere dheere");
        WORD_REPLACEMENTS.put("quickly", "jaldi se");
    }

    /**
     * Translates English text to simulated Hinglish using strict WHOLE-WORD boundaries (\b).
     * Preserves capitalization (e.g. "If" -> "Agar", "if" -> "agar", "IF" -> "AGAR").
     * Never replaces substrings inside words like "manifest", "different", "life", "wife".
     */
    public static String translate(String text, int hinglishLevel) {
        if (text == null || text.isBlank()) return text;

        String result = text;

        for (Map.Entry<String, String> entry : WORD_REPLACEMENTS.entrySet()) {
            String english = entry.getKey();
            String hindi = entry.getValue();

            // Word boundary regex: \b...\b
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(english) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(result);
            StringBuilder sb = new StringBuilder();

            while (matcher.find()) {
                String original = matcher.group();
                String replacement = matchCase(original, hindi);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    private static String matchCase(String original, String replacement) {
        if (original.isEmpty()) return replacement;

        // All uppercase: "IF" -> "AGAR"
        if (original.length() > 1 && original.equals(original.toUpperCase())) {
            return replacement.toUpperCase();
        }

        // Title case: "If" -> "Agar"
        if (Character.isUpperCase(original.charAt(0))) {
            if (replacement.length() <= 1) {
                return replacement.toUpperCase();
            }
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1).toLowerCase();
        }

        // Lowercase: "if" -> "agar"
        return replacement.toLowerCase();
    }
}
