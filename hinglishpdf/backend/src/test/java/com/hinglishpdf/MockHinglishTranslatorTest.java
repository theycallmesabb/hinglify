package com.hinglishpdf;

import com.hinglishpdf.llm.MockHinglishTranslator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockHinglishTranslatorTest {

    @Test
    void shouldNeverBreakSubstringsInsideWords() {
        String input = "The manifestation of different life and wife scenarios was clear.";
        String output = MockHinglishTranslator.translate(input, 2);

        // Substrings must NOT be replaced
        assertFalse(output.contains("manAgarest"), "manifestation was incorrectly broken!");
        assertFalse(output.contains("dAgarferent"), "different was incorrectly broken!");
        assertFalse(output.contains("lAgare"), "life was incorrectly broken!");
        assertFalse(output.contains("wAgare"), "wife was incorrectly broken!");

        assertTrue(output.contains("manifestation"));
        assertTrue(output.contains("different"));
        assertTrue(output.contains("life"));
        assertTrue(output.contains("wife"));
    }

    @Test
    void shouldReplaceWholeWordsWithCorrectCase() {
        String input = "If you go, if they come, IF it happens. When she smiled, when we met. Because of rain, because it was cold. But they agreed, but not now. So be it, so simple.";
        String output = MockHinglishTranslator.translate(input, 2);

        // Check capitalized replacements
        assertTrue(output.contains("Agar you go"));
        assertTrue(output.contains("agar they come"));
        assertTrue(output.contains("AGAR it happens"));

        assertTrue(output.contains("Jab she smiled"));
        assertTrue(output.contains("jab we met"));

        assertTrue(output.contains("Kyunki of rain"));
        assertTrue(output.contains("kyunki it was cold"));

        assertTrue(output.contains("Lekin they agreed"));
        assertTrue(output.contains("lekin not now"));

        assertTrue(output.contains("Isliye be it"));
        assertTrue(output.contains("isliye simple"));
    }
}
