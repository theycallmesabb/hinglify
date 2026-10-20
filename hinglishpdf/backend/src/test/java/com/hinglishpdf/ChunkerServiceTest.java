package com.hinglishpdf;

import com.hinglishpdf.service.ChunkerService;
import com.hinglishpdf.service.TextCleanerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerServiceTest {

    private ChunkerService chunkerService;

    @BeforeEach
    void setUp() {
        chunkerService = new ChunkerService();
    }

    @Test
    void shouldEstimateTokensAsCharsDividedByFour() {
        String text = "This is a sentence of exactly forty characters.";
        // 47 chars / 4 => 12 tokens
        List<TextCleanerService.CleanedBlock> blocks = List.of(
                new TextCleanerService.CleanedBlock(text, false, 1)
        );

        List<ChunkerService.Chunk> chunks = chunkerService.chunk(blocks);
        assertEquals(1, chunks.size());
        assertEquals((int) Math.ceil(text.length() / 4.0), chunks.get(0).estimatedTokens());
    }

    @Test
    void shouldNeverSplitInMiddleOfSentence() {
        // Construct a block with 3 complete sentences
        String sentence1 = "First sentence is short and sweet.";
        String sentence2 = "Second sentence provides critical technical details about the architecture.";
        String sentence3 = "Third sentence closes out the paragraph.";

        List<TextCleanerService.CleanedBlock> blocks = List.of(
                new TextCleanerService.CleanedBlock(sentence1, false, 1),
                new TextCleanerService.CleanedBlock(sentence2, false, 1),
                new TextCleanerService.CleanedBlock(sentence3, false, 1)
        );

        List<ChunkerService.Chunk> chunks = chunkerService.chunk(blocks);
        for (ChunkerService.Chunk chunk : chunks) {
            String t = chunk.text().trim();
            assertTrue(t.endsWith(".") || t.endsWith("!") || t.endsWith("?"));
        }
    }

    @Test
    void shouldPreserveHeadingFlagWhenChunkIsHeadingOnly() {
        List<TextCleanerService.CleanedBlock> blocks = List.of(
                new TextCleanerService.CleanedBlock("Chapter 1: Database Architecture", true, 1)
        );

        List<ChunkerService.Chunk> chunks = chunkerService.chunk(blocks);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).isHeading());
    }
}
