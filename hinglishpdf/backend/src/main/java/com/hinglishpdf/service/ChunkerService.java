package com.hinglishpdf.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkerService {

    // 800 - 1200 tokens (chars / 4 => 3200 to 4800 characters)
    public static final int MIN_CHUNK_CHARS = 2400; // ~600 tokens
    public static final int TARGET_CHUNK_CHARS = 3800; // ~950 tokens
    public static final int MAX_CHUNK_CHARS = 4800; // ~1200 tokens

    public record Chunk(String text, boolean isHeading, int estimatedTokens) {}

    /**
     * Splits a list of cleaned blocks into paragraph/sentence-aware chunks
     * of approximately 800-1200 tokens without cutting mid-sentence.
     */
    public List<Chunk> chunk(List<TextCleanerService.CleanedBlock> blocks) {
        List<Chunk> chunks = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) {
            return chunks;
        }

        StringBuilder currentChunk = new StringBuilder();
        boolean currentIsHeadingOnly = false;

        for (TextCleanerService.CleanedBlock block : blocks) {
            String blockText = block.text().trim();
            if (blockText.isEmpty()) continue;

            // If a block alone exceeds max chunk chars, split it by sentence boundaries
            if (blockText.length() > MAX_CHUNK_CHARS) {
                // Flush existing accumulator
                if (currentChunk.length() > 0) {
                    chunks.add(createChunk(currentChunk.toString(), currentIsHeadingOnly));
                    currentChunk.setLength(0);
                    currentIsHeadingOnly = false;
                }

                List<String> sentenceChunks = splitBySentences(blockText, TARGET_CHUNK_CHARS, MAX_CHUNK_CHARS);
                for (String sc : sentenceChunks) {
                    chunks.add(createChunk(sc, block.isHeading()));
                }
                continue;
            }

            // Check if adding this block exceeds target/max chunk size
            if (currentChunk.length() > 0 && (currentChunk.length() + blockText.length() + 2 > MAX_CHUNK_CHARS)) {
                chunks.add(createChunk(currentChunk.toString(), currentIsHeadingOnly));
                currentChunk.setLength(0);
                currentIsHeadingOnly = false;
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            } else {
                currentIsHeadingOnly = block.isHeading();
            }

            // If we are merging body text into a heading, it's no longer just a heading
            if (!block.isHeading() && currentIsHeadingOnly) {
                currentIsHeadingOnly = false;
            }

            currentChunk.append(blockText);
        }

        if (currentChunk.length() > 0) {
            chunks.add(createChunk(currentChunk.toString(), currentIsHeadingOnly));
        }

        return chunks;
    }

    private List<String> splitBySentences(String text, int targetChars, int maxChars) {
        List<String> result = new ArrayList<>();
        // Split on sentence boundaries: period/question/exclamation followed by space or newline
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder sb = new StringBuilder();
        for (String sentence : sentences) {
            if (sb.length() > 0 && (sb.length() + sentence.length() + 1 > maxChars)) {
                result.add(sb.toString().trim());
                sb.setLength(0);
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(sentence);
        }

        if (sb.length() > 0) {
            result.add(sb.toString().trim());
        }
        return result;
    }

    private Chunk createChunk(String text, boolean isHeading) {
        int tokens = (int) Math.ceil((double) text.length() / 4.0);
        return new Chunk(text, isHeading, tokens);
    }
}
