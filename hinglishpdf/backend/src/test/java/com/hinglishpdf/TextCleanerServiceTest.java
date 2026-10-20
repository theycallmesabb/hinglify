package com.hinglishpdf;

import com.hinglishpdf.dto.ExtractorResponse;
import com.hinglishpdf.service.TextCleanerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextCleanerServiceTest {

    private TextCleanerService cleanerService;

    @BeforeEach
    void setUp() {
        cleanerService = new TextCleanerService();
    }

    @Test
    void shouldJoinHyphenatedLineBreaks() {
        ExtractorResponse.BlockInfo block = new ExtractorResponse.BlockInfo(
                "This is an archi-\ntectural blueprint for imple-\nmentation and trans-\nlation.",
                12.0, List.of(80.0, 150.0, 400.0, 300.0), false
        );
        ExtractorResponse.PageInfo page = new ExtractorResponse.PageInfo(1, 100, List.of(block));
        ExtractorResponse response = new ExtractorResponse(1, 100, 100.0, List.of(page));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(1, cleaned.size());
        assertTrue(cleaned.get(0).text().contains("architectural"));
        assertTrue(cleaned.get(0).text().contains("implementation"));
        assertTrue(cleaned.get(0).text().contains("translation"));
        assertFalse(cleaned.get(0).text().contains("archi-"));
    }

    @Test
    void shouldPreserveIntentionalHyphens() {
        ExtractorResponse.BlockInfo block = new ExtractorResponse.BlockInfo(
                "A novelist, short-\nstory writer who drove down a two-\nway street in an old-\nfashioned thirty-\nfour year old car.",
                12.0, List.of(80.0, 150.0, 400.0, 300.0), false
        );
        ExtractorResponse.PageInfo page = new ExtractorResponse.PageInfo(1, 120, List.of(block));
        ExtractorResponse response = new ExtractorResponse(1, 120, 120.0, List.of(page));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(1, cleaned.size());
        String text = cleaned.get(0).text();
        assertTrue(text.contains("short-story"), "Should preserve short-story hyphen, got: " + text);
        assertTrue(text.contains("two-way"), "Should preserve two-way hyphen, got: " + text);
        assertTrue(text.contains("old-fashioned"), "Should preserve old-fashioned hyphen, got: " + text);
        assertTrue(text.contains("thirty-four"), "Should preserve thirty-four hyphen, got: " + text);
    }

    @Test
    void shouldFilterStandalonePageNumbers() {
        ExtractorResponse.BlockInfo p1 = new ExtractorResponse.BlockInfo("Page 1 of 3", 10.0, List.of(100.0, 70.0, 200.0, 80.0), false);
        ExtractorResponse.BlockInfo content = new ExtractorResponse.BlockInfo("Real technical content", 12.0, List.of(80.0, 150.0, 400.0, 300.0), false);
        ExtractorResponse.BlockInfo pNum = new ExtractorResponse.BlockInfo("1", 9.0, List.of(200.0, 750.0, 250.0, 760.0), false);
        ExtractorResponse.BlockInfo slashNum = new ExtractorResponse.BlockInfo("3/", 9.0, List.of(80.0, 70.0, 100.0, 80.0), false);

        ExtractorResponse.PageInfo page = new ExtractorResponse.PageInfo(1, 50, List.of(p1, content, pNum, slashNum));
        ExtractorResponse response = new ExtractorResponse(1, 50, 50.0, List.of(page));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(1, cleaned.size());
        assertEquals("Real technical content", cleaned.get(0).text());
    }

    @Test
    void shouldFilterRunningHeadersWithChangingPageNumbers() {
        // Headers like "1/I SELL MY DREAMS" on Page 1, "3/I SELL MY DREAMS" on Page 3, "2/KALEIDOSCOPE" on Page 2
        ExtractorResponse.BlockInfo h1 = new ExtractorResponse.BlockInfo(
                "1/I SELL MY DREAMS", 8.0, List.of(335.0, 68.0, 405.0, 76.0), false, true
        );
        ExtractorResponse.BlockInfo b1 = new ExtractorResponse.BlockInfo(
                "Content of page 1", 11.0, List.of(80.0, 120.0, 400.0, 500.0), false
        );

        ExtractorResponse.BlockInfo h2 = new ExtractorResponse.BlockInfo(
                "2/KALEIDOSCOPE", 8.0, List.of(80.0, 68.0, 134.0, 76.0), false, true
        );
        ExtractorResponse.BlockInfo b2 = new ExtractorResponse.BlockInfo(
                "Content of page 2", 11.0, List.of(80.0, 120.0, 400.0, 500.0), false
        );

        ExtractorResponse.BlockInfo h3 = new ExtractorResponse.BlockInfo(
                "3/I SELL MY DREAMS", 8.0, List.of(335.0, 68.0, 405.0, 76.0), false, true
        );
        ExtractorResponse.BlockInfo b3 = new ExtractorResponse.BlockInfo(
                "Content of page 3", 11.0, List.of(80.0, 120.0, 400.0, 500.0), false
        );

        ExtractorResponse.PageInfo page1 = new ExtractorResponse.PageInfo(1, 486.0, 678.0, 50, List.of(h1, b1));
        ExtractorResponse.PageInfo page2 = new ExtractorResponse.PageInfo(2, 486.0, 678.0, 50, List.of(h2, b2));
        ExtractorResponse.PageInfo page3 = new ExtractorResponse.PageInfo(3, 486.0, 678.0, 50, List.of(h3, b3));
        ExtractorResponse response = new ExtractorResponse(3, 150, 50.0, List.of(page1, page2, page3));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(3, cleaned.size());
        assertEquals("Content of page 1", cleaned.get(0).text());
        assertEquals("Content of page 2", cleaned.get(1).text());
        assertEquals("Content of page 3", cleaned.get(2).text());
    }

    @Test
    void shouldFilterRunningFootersByMargin() {
        // Footers like "Reprint 2026-27" located at bottom of page
        ExtractorResponse.BlockInfo b1 = new ExtractorResponse.BlockInfo(
                "Page 1 story text.", 11.0, List.of(80.0, 120.0, 400.0, 500.0), false
        );
        ExtractorResponse.BlockInfo f1 = new ExtractorResponse.BlockInfo(
                "Reprint 2026-27", 8.0, List.of(214.0, 655.0, 271.0, 666.0), false
        );

        ExtractorResponse.BlockInfo b2 = new ExtractorResponse.BlockInfo(
                "Page 2 story text.", 11.0, List.of(80.0, 120.0, 400.0, 500.0), false
        );
        ExtractorResponse.BlockInfo f2 = new ExtractorResponse.BlockInfo(
                "Reprint 2026-27", 8.0, List.of(214.0, 655.0, 271.0, 666.0), false
        );

        ExtractorResponse.PageInfo page1 = new ExtractorResponse.PageInfo(1, 486.0, 678.0, 50, List.of(b1, f1));
        ExtractorResponse.PageInfo page2 = new ExtractorResponse.PageInfo(2, 486.0, 678.0, 50, List.of(b2, f2));
        ExtractorResponse response = new ExtractorResponse(2, 100, 50.0, List.of(page1, page2));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(2, cleaned.size());
        assertEquals("Page 1 story text.", cleaned.get(0).text());
        assertEquals("Page 2 story text.", cleaned.get(1).text());
    }

    @Test
    void shouldDeduplicateRepeatedDesignLines() {
        // Block with repeated decorative lines like 'Stop and Think' repeated 5 times, and '11111'
        String repeatedBlock = "Stop and Think\nStop and Think\nStop and Think\nStop and Think";
        ExtractorResponse.BlockInfo block = new ExtractorResponse.BlockInfo(
                repeatedBlock, 14.0, List.of(195.0, 86.0, 284.0, 98.0), true
        );
        ExtractorResponse.BlockInfo digitBlock = new ExtractorResponse.BlockInfo(
                "11111", 40.0, List.of(392.0, 142.0, 413.0, 183.0), true
        );

        ExtractorResponse.PageInfo page = new ExtractorResponse.PageInfo(1, 486.0, 678.0, 50, List.of(block, digitBlock));
        ExtractorResponse response = new ExtractorResponse(1, 50, 50.0, List.of(page));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(2, cleaned.size());
        assertEquals("Stop and Think", cleaned.get(0).text());
        assertEquals("1", cleaned.get(1).text());
    }

    @Test
    void shouldMergeCrossPageParagraphContinuation() {
        // Page 1 ends mid-sentence: "...gate to the sea and installed another,"
        // Page 2 begins: "and everything returned to normal."
        ExtractorResponse.BlockInfo page1End = new ExtractorResponse.BlockInfo(
                "The volunteers sealed off the gate to the sea and installed another,",
                11.0, List.of(80.0, 500.0, 400.0, 600.0), false
        );
        ExtractorResponse.BlockInfo page2Start = new ExtractorResponse.BlockInfo(
                "and everything returned to normal. During the morning nobody worried.",
                11.0, List.of(80.0, 87.0, 400.0, 200.0), false
        );

        ExtractorResponse.PageInfo page1 = new ExtractorResponse.PageInfo(1, 486.0, 678.0, 50, List.of(page1End));
        ExtractorResponse.PageInfo page2 = new ExtractorResponse.PageInfo(2, 486.0, 678.0, 50, List.of(page2Start));
        ExtractorResponse response = new ExtractorResponse(2, 100, 50.0, List.of(page1, page2));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(1, cleaned.size(), "Should have merged split paragraph across pages into a single block");
        assertEquals(
                "The volunteers sealed off the gate to the sea and installed another, and everything returned to normal. During the morning nobody worried.",
                cleaned.get(0).text()
        );
        assertEquals(1, cleaned.get(0).pageNumber());
    }

    @Test
    void shouldPreserveAuthorCaptionWithDates() {
        ExtractorResponse.BlockInfo caption = new ExtractorResponse.BlockInfo(
                "Gabriel Garcia Marquez\n1927-2014",
                8.0, List.of(82.0, 356.0, 178.0, 374.0), false
        );
        ExtractorResponse.PageInfo page = new ExtractorResponse.PageInfo(1, 486.0, 678.0, 30, List.of(caption));
        ExtractorResponse response = new ExtractorResponse(1, 30, 30.0, List.of(page));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        assertEquals(1, cleaned.size());
        assertEquals("Gabriel Garcia Marquez\n1927-2014", cleaned.get(0).text());
    }

    @Test
    void shouldDeduplicateCrossPageOverlap() {
        // Page 1 ends with "...installed another and everything returned to normal."
        // Page 2 begins with "and everything returned to normal. We then proceeded to the hotel terrace."
        ExtractorResponse.BlockInfo p1 = new ExtractorResponse.BlockInfo(
                "The volunteers worked hard, installed another and everything returned to normal.",
                11.0, List.of(80.0, 500.0, 400.0, 600.0), false
        );
        ExtractorResponse.BlockInfo p2 = new ExtractorResponse.BlockInfo(
                "and everything returned to normal. We then proceeded to the hotel terrace.",
                11.0, List.of(80.0, 100.0, 400.0, 200.0), false
        );

        ExtractorResponse.PageInfo page1 = new ExtractorResponse.PageInfo(1, 486.0, 678.0, 100, List.of(p1));
        ExtractorResponse.PageInfo page2 = new ExtractorResponse.PageInfo(2, 486.0, 678.0, 100, List.of(p2));
        ExtractorResponse response = new ExtractorResponse(2, 200, 100.0, List.of(page1, page2));

        List<TextCleanerService.CleanedBlock> cleaned = cleanerService.clean(response);

        // Overlapping "and everything returned to normal." should be deduplicated
        assertEquals(2, cleaned.size());
        assertEquals("The volunteers worked hard, installed another and everything returned to normal.", cleaned.get(0).text());
        assertEquals("We then proceeded to the hotel terrace.", cleaned.get(1).text());
    }
}
