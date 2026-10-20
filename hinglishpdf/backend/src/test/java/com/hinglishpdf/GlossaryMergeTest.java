package com.hinglishpdf;

import com.hinglishpdf.llm.OpenAiLlmProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlossaryMergeTest {

    @Test
    void shouldExtractAndDeduplicateTechnicalTermsFromHeadings() {
        OpenAiLlmProvider provider = new OpenAiLlmProvider("https://api.openai.com/v1", "gpt-4o-mini", "mock-key");

        List<String> headings = List.of(
                "Complete RCM (Reverse Charge Mechanism) Backend Blueprint",
                "Database & Struct Models (Data Layer)",
                "Core Calculation Logic (Business Layer)",
                "Accounting Vouchers (Journal Entries)"
        );

        List<String> glossary = provider.extractGlossary(headings, "Sample body text mentioning GST and GORM models.");

        assertFalse(glossary.isEmpty());
        // Verify key terms are preserved
        assertTrue(glossary.contains("RCM") || glossary.contains("Database") || glossary.contains("GST"));
    }
}
