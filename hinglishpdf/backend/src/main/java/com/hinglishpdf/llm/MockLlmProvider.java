package com.hinglishpdf.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MockLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(MockLlmProvider.class);

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public List<String> extractGlossary(List<String> headings, String sampleText) {
        Set<String> terms = new LinkedHashSet<>(List.of("API", "PDF", "GST", "RCM", "GORM", "Golang", "Database", "Invoice", "DTO"));
        if (headings != null) {
            for (String h : headings) {
                for (String word : h.split("\\s+")) {
                    String clean = word.replaceAll("[^a-zA-Z]", "");
                    if (clean.length() > 3 && Character.isUpperCase(clean.charAt(0))) {
                        terms.add(clean);
                    }
                }
            }
        }
        return new ArrayList<>(terms);
    }

    @Override
    public String translateChunk(String text, int hinglishLevel, List<String> glossary, String previousContext) {
        log.debug("Translating chunk via MockLlmProvider (Level {})", hinglishLevel);
        return MockHinglishTranslator.translate(text, hinglishLevel);
    }
}
