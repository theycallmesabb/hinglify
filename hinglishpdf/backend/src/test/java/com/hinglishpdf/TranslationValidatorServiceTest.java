package com.hinglishpdf;

import com.hinglishpdf.service.TranslationValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TranslationValidatorServiceTest {

    private TranslationValidatorService validator;

    @BeforeEach
    void setUp() {
        validator = new TranslationValidatorService();
    }

    @Test
    void shouldExtractNumbersAndStandardizeSpelledOutNumbers() {
        String source = "He was thirty-four years old in 1982 and had twenty-six cars.";
        Set<String> numbers = validator.extractAllNumbers(source);

        assertTrue(numbers.contains("34"), "Should standardize 'thirty-four' to '34'");
        assertTrue(numbers.contains("1982"), "Should extract digit '1982'");
        assertTrue(numbers.contains("26"), "Should standardize 'twenty-six' to '26'");
    }

    @Test
    void shouldPassValidHinglishTranslationWithNumbersPreserved() {
        String source = "He was thirty-four years old when he arrived at the hotel in 1982.";
        String target = "Uski age 34 years thi jab woh 1982 mein hotel pahuncha.";

        TranslationValidatorService.ValidationReport report = validator.validate(source, target);

        assertTrue(report.isValid(), "Valid Hinglish with preserved numbers should pass: " + report.issues());
        assertFalse(report.hasSevereIssues());
    }

    @Test
    void shouldFlagMissingNumbersInTarget() {
        String source = "The price was 450 dollars and thirty-four people attended.";
        String target = "Price bohot zyada tha aur bohot saare log aaye the."; // 450 and 34 missing!

        TranslationValidatorService.ValidationReport report = validator.validate(source, target);

        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(i -> i.contains("450")));
        assertTrue(report.issues().stream().anyMatch(i -> i.contains("34")));
    }

    @Test
    void shouldFlagUntranslatedEnglishSentences() {
        String source = "We sat at the terrace. The waiter brought the drinks without delay.";
        // Second sentence is completely untranslated English
        String target = "Hum terrace par baithe the. The waiter brought the drinks without delay.";

        TranslationValidatorService.ValidationReport report = validator.validate(source, target);

        assertFalse(report.isValid());
        assertTrue(report.hasSevereIssues());
        assertEquals(1, report.untranslatedEnglishSentences().size());
        assertTrue(report.untranslatedEnglishSentences().get(0).contains("The waiter brought"));
    }

    @Test
    void shouldFlagSevereSentenceOmission() {
        String source = "Sentence one is here. Sentence two describes the event. Sentence three explains the reason. Sentence four concludes the section.";
        String target = "Bas ek sentence bacha hai."; // 4 sentences collapsed into 1

        TranslationValidatorService.ValidationReport report = validator.validate(source, target);

        assertFalse(report.isValid());
        assertTrue(report.hasSevereIssues());
        assertTrue(report.issues().stream().anyMatch(i -> i.contains("Sentence omission detected")));
    }

    @Test
    void shouldFlagPotentialPolarityInversion() {
        String source = "He never returned to Vienna after that day.";
        String target = "Woh us din ke baad Vienna laut gaya."; // Missing 'nahi' or 'kabhi nahi'

        TranslationValidatorService.ValidationReport report = validator.validate(source, target);

        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(i -> i.contains("polarity inversion")));
    }

    @Test
    void shouldCleanPromptLeakageAndStuttering() {
        String dirty = "Here is the most natural spoken Hinglish translation:\n" +
                "Main soooo khush tha aur hum hotel gaye.";

        String cleaned = validator.cleanAndNormalize(dirty);

        assertFalse(cleaned.startsWith("Here is the"));
        assertTrue(cleaned.contains("Main so khush tha"));
    }

    @Test
    void shouldFlagBrokenSentenceWithHangingConnector() {
        String source = "We walked into the store and we bought fresh fruits.";
        String target = "Hum store ke andar gaye aur"; // ends with hanging 'aur'

        TranslationValidatorService.ValidationReport report = validator.validate(source, target);

        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(i -> i.contains("hanging connector")));
    }
}
