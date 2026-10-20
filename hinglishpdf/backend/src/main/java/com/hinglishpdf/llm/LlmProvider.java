package com.hinglishpdf.llm;

import java.util.List;

public interface LlmProvider {

    String getName();

    boolean isMock();

    /**
     * Extracts technical terms and keywords from headings and document sample text
     * that should remain in English across all chunks.
     */
    List<String> extractGlossary(List<String> headings, String sampleText);

    /**
     * Translates a single chunk to Hinglish based on level (1, 2, 3), glossary, and previous context.
     */
    String translateChunk(String text, int hinglishLevel, List<String> glossary, String previousContext);
}
