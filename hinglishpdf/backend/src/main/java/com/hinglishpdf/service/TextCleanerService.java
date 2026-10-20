package com.hinglishpdf.service;

import com.hinglishpdf.dto.ExtractorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextCleanerService {

    private static final Logger log = LoggerFactory.getLogger(TextCleanerService.class);

    private static final Pattern STANDALONE_PAGE_NUMBER = Pattern.compile(
            "^(page\\s+\\d+(\\s+of\\s+\\d+)?|\\d+\\s*/\\s*\\d+|\\d+|\\d+\\s*/\\s*|[ivxlcdm]+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LEADING_PAGE_PREFIX = Pattern.compile(
            "^(\\s*\\d+\\s*[/\\-–—|]\\s*|\\s*page\\s*\\d+\\s*|\\s*[ivxlcdm]+\\s*[/\\-–—|]\\s*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRAILING_PAGE_SUFFIX = Pattern.compile(
            "(\\s*[/\\-–—|]\\s*\\d+\\s*|\\s*page\\s*\\d+\\s*|\\s*[/\\-–—|]\\s*[ivxlcdm]+\\s*)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HYPHEN_LINE_BREAK = Pattern.compile(
            "\\b([a-zA-Z]+)-\\s*\\n\\s*([a-zA-Z]+)\\b"
    );

    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile(
            "^\\s*\\(?\\d{4}\\s*[-–—]\\s*\\d{4}\\)?\\s*$"
    );

    private static final Set<String> COMPOUND_PREFIXES = Set.of(
            "short", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
            "old", "new", "well", "ill", "half", "part", "self", "non", "anti", "all",
            "cross", "multi", "co", "ex", "pre", "post", "first", "second", "third",
            "high", "low", "full", "wide", "near", "far", "out", "over", "under"
    );

    private static final Set<String> COMPOUND_SUFFIXES = Set.of(
            "story", "way", "fashioned", "year", "years", "day", "days", "time", "hand",
            "known", "making", "maker", "looking", "faced", "minded", "rate", "scale",
            "depth", "level", "based", "driven", "free", "bound", "proof", "tight"
    );

    private static final Set<String> CLOSED_COMPOUNDS = Set.of(
            "something", "everyone", "someone", "everything", "anyone", "anything",
            "inside", "outside", "cannot", "into", "without", "upon", "today",
            "tomorrow", "tonight", "forever", "meanwhile", "sometimes", "anyway",
            "background", "together", "understand", "understandable", "nevertheless"
    );

    private static final Set<String> KNOWN_FOOTER_PATTERNS = Set.of(
            "reprint", "allrightsreserved", "copyright", "isbn", "printedin"
    );

    public record CleanedBlock(String text, boolean isHeading, int pageNumber) {}

    /**
     * Cleans extracted blocks:
     * 1. Filters out standalone page numbers.
     * 2. Filters out running headers and footers using margin position and cross-page repetition.
     * 3. Deduplicates consecutive repeated design lines (e.g. repeated 'Stop and Think').
     * 4. Intelligently handles hyphens (preserves 'short-story', 'two-way', 'old-fashioned'; unhyphenates 'com-plete').
     * 5. Joins paragraphs split across page boundaries without terminating punctuation.
     * 6. Preserves captions and normalizes erratic line breaks.
     */
    public List<CleanedBlock> clean(ExtractorResponse extracted) {
        if (extracted == null || extracted.pages() == null || extracted.pages().isEmpty()) {
            return Collections.emptyList();
        }

        int totalPages = extracted.pageCount();

        // 1. Identify running headers and footers by analyzing candidate blocks in margins
        Map<String, Set<Integer>> candidatePageOccurrences = new HashMap<>();

        for (ExtractorResponse.PageInfo page : extracted.pages()) {
            if (page.blocks() == null) continue;
            double pageH = (page.pageHeight() != null && page.pageHeight() > 0) ? page.pageHeight() : 792.0;

            for (ExtractorResponse.BlockInfo block : page.blocks()) {
                if (block.text() == null || block.text().isBlank()) continue;
                String raw = block.text().trim();

                if (isMarginBlock(block, pageH)) {
                    String normCore = extractHeaderFooterCore(raw);
                    if (!normCore.isEmpty()) {
                        candidatePageOccurrences
                                .computeIfAbsent(normCore, k -> new HashSet<>())
                                .add(page.pageNumber());
                    }
                }
            }
        }

        // Determine which normalized cores are repeated across >= 2 pages or match known patterns
        Set<String> runningHeadersFooters = new HashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : candidatePageOccurrences.entrySet()) {
            String core = entry.getKey();
            int occurrences = entry.getValue().size();
            if (occurrences >= 2 || (totalPages <= 2 && occurrences >= 1 && isKnownFooterCore(core))) {
                runningHeadersFooters.add(core);
            }
        }

        // 2. Process and filter blocks page-by-page
        List<CleanedBlock> initialCleaned = new ArrayList<>();

        for (ExtractorResponse.PageInfo page : extracted.pages()) {
            if (page.blocks() == null) continue;
            double pageH = (page.pageHeight() != null && page.pageHeight() > 0) ? page.pageHeight() : 792.0;

            for (ExtractorResponse.BlockInfo block : page.blocks()) {
                if (block.text() == null || block.text().isBlank()) continue;
                String rawText = block.text().trim();

                // Skip standalone page numbers (only if not a prominent heading, and either in margin or small font)
                boolean isStandalonePageNum = STANDALONE_PAGE_NUMBER.matcher(rawText).matches();
                if (isStandalonePageNum && !block.isHeading() && (isMarginBlock(block, pageH) || block.fontSize() <= 12.0)) {
                    continue;
                }

                // Skip running headers and footers
                if (isMarginBlock(block, pageH)) {
                    String normCore = extractHeaderFooterCore(rawText);
                    boolean hasPagePrefixOrSuffix = LEADING_PAGE_PREFIX.matcher(rawText).find() || TRAILING_PAGE_SUFFIX.matcher(rawText).find();
                    if (runningHeadersFooters.contains(normCore) || isKnownFooterCore(normCore) || hasPagePrefixOrSuffix || isStandalonePageNum) {
                        continue;
                    }
                }

                // Deduplicate consecutive identical lines within block
                String dedupedText = deduplicateLines(rawText);

                // Handle hyphenated line breaks
                String hyphenCleaned = handleHyphenatedLineBreaks(dedupedText);

                // Clean soft line breaks while preserving paragraph boundaries and captions
                String cleanedText = cleanSoftLineBreaks(hyphenCleaned);

                if (!cleanedText.isEmpty()) {
                    initialCleaned.add(new CleanedBlock(cleanedText, block.isHeading(), page.pageNumber()));
                }
            }
        }

        // 3. Cross-page paragraph continuation: merge blocks split across page boundaries
        return mergeCrossPageParagraphs(initialCleaned);
    }

    private boolean isMarginBlock(ExtractorResponse.BlockInfo block, double pageHeight) {
        if (Boolean.TRUE.equals(block.isHeaderFooter())) {
            return true;
        }
        if (block.bbox() != null && block.bbox().size() >= 4) {
            double y0 = block.bbox().get(1);
            double y1 = block.bbox().get(3);
            // Header margin: within top 13% of page
            if (y1 <= pageHeight * 0.13 || y0 <= pageHeight * 0.10) {
                return true;
            }
            // Footer margin: within bottom 10% of page
            if (y0 >= pageHeight * 0.89) {
                return true;
            }
        }
        return false;
    }

    private String extractHeaderFooterCore(String text) {
        String s = text.trim();
        s = LEADING_PAGE_PREFIX.matcher(s).replaceAll("");
        s = TRAILING_PAGE_SUFFIX.matcher(s).replaceAll("");
        // Remove all non-alphanumeric and lowercase
        return s.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private boolean isKnownFooterCore(String core) {
        if (core == null || core.isEmpty()) return false;
        for (String pattern : KNOWN_FOOTER_PATTERNS) {
            if (core.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String deduplicateLines(String text) {
        String[] lines = text.split("\n");
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // Collapse repeated identical character tokens (e.g. '11111' -> '1')
            if (trimmed.length() >= 3 && trimmed.chars().distinct().count() == 1) {
                trimmed = String.valueOf(trimmed.charAt(0));
            }
            if (result.isEmpty() || !result.get(result.size() - 1).equals(trimmed)) {
                result.add(trimmed);
            }
        }
        return String.join("\n", result);
    }

    private String handleHyphenatedLineBreaks(String text) {
        Matcher matcher = HYPHEN_LINE_BREAK.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String word1 = matcher.group(1);
            String word2 = matcher.group(2);
            String w1Lower = word1.toLowerCase();
            String w2Lower = word2.toLowerCase();
            String joinedCombined = (w1Lower + w2Lower);

            boolean isCompound = COMPOUND_PREFIXES.contains(w1Lower)
                    || COMPOUND_SUFFIXES.contains(w2Lower)
                    || (w1Lower.length() >= 3 && w2Lower.length() >= 3 && !CLOSED_COMPOUNDS.contains(joinedCombined) && isCommonCompoundWord(w1Lower, w2Lower));

            if (CLOSED_COMPOUNDS.contains(joinedCombined)) {
                matcher.appendReplacement(sb, word1 + word2);
            } else if (isCompound) {
                // Preserve intentional hyphen (e.g. short-story, two-way, old-fashioned, thirty-four)
                matcher.appendReplacement(sb, word1 + "-" + word2);
            } else {
                // Remove hyphen for split syllables (e.g. com-plete -> complete, espi-onage -> espionage)
                matcher.appendReplacement(sb, word1 + word2);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isCommonCompoundWord(String w1, String w2) {
        return (w1.equals("short") && w2.equals("story"))
                || (w1.equals("two") && w2.equals("way"))
                || (w1.equals("old") && w2.equals("fashioned"))
                || (w1.equals("thirty") && w2.equals("four"))
                || (w1.equals("twenty") && w2.equals("six"))
                || (w1.equals("well") && w2.equals("known"));
    }

    private String cleanSoftLineBreaks(String text) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (sb.length() > 0) {
                // Check if this line is a caption date range (e.g. "1927-2014")
                if (YEAR_RANGE_PATTERN.matcher(line).matches()) {
                    sb.append("\n");
                } else {
                    char lastChar = sb.charAt(sb.length() - 1);
                    // If previous line doesn't end in sentence-ending punctuation or colon, join with space
                    if (lastChar != '.' && lastChar != ':' && lastChar != '!' && lastChar != '?'
                            && lastChar != '”' && lastChar != '"' && lastChar != ';') {
                        sb.append(" ");
                    } else {
                        sb.append("\n");
                    }
                }
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private List<CleanedBlock> mergeCrossPageParagraphs(List<CleanedBlock> blocks) {
        if (blocks.size() <= 1) {
            return blocks;
        }

        List<CleanedBlock> merged = new ArrayList<>();
        CleanedBlock current = blocks.get(0);

        for (int i = 1; i < blocks.size(); i++) {
            CleanedBlock next = blocks.get(i);

            // Step 1: Detect and trim page overlap between adjacent blocks/pages
            next = trimPageOverlap(current, next);
            if (next.text().trim().isEmpty()) {
                // Entire block was an overlapping catchword/duplicate line; skip it
                continue;
            }

            // Step 2: Check if next continues current's sentence across page boundary
            if (canMergeCrossPage(current, next)) {
                String combinedText = current.text() + " " + next.text();
                current = new CleanedBlock(combinedText, current.isHeading(), current.pageNumber());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * Detects and removes duplicated text across adjacent pages or blocks.
     * E.g. if current ends with "...installed another and everything returned to normal."
     * and next starts with "and everything returned to normal. We then proceeded...",
     * the duplicated prefix in next is identified and trimmed.
     */
    private CleanedBlock trimPageOverlap(CleanedBlock prev, CleanedBlock next) {
        if (prev == null || next == null || prev.isHeading() || next.isHeading()) {
            return next;
        }

        String prevText = prev.text().trim();
        String nextText = next.text().trim();
        if (prevText.isEmpty() || nextText.isEmpty()) {
            return next;
        }

        String[] prevTokens = prevText.split("\\s+");
        String[] nextTokens = nextText.split("\\s+");

        // Maximum overlap window to check (up to 40 words)
        int maxCheck = Math.min(40, Math.min(prevTokens.length, nextTokens.length));
        int bestOverlapWordCount = 0;
        int bestNextCharIndex = 0;

        for (int k = maxCheck; k >= 3; k--) {
            boolean match = true;
            for (int i = 0; i < k; i++) {
                String wPrev = normalizeWord(prevTokens[prevTokens.length - k + i]);
                String wNext = normalizeWord(nextTokens[i]);
                if (!wPrev.equals(wNext)) {
                    match = false;
                    break;
                }
            }

            if (match) {
                int charLen = 0;
                for (int i = 0; i < k; i++) {
                    charLen += nextTokens[i].length();
                }
                if (charLen >= 12 || k >= 4) {
                    bestOverlapWordCount = k;
                    bestNextCharIndex = findCharOffsetAfterTokens(nextText, k);
                    break;
                }
            }
        }

        if (bestOverlapWordCount > 0) {
            String trimmedNext = nextText.substring(bestNextCharIndex).trim();
            log.info("Detected page-overlap across page {} and {}: {} words trimmed.",
                    prev.pageNumber(), next.pageNumber(), bestOverlapWordCount);
            return new CleanedBlock(trimmedNext, next.isHeading(), next.pageNumber());
        }

        return next;
    }

    private String normalizeWord(String word) {
        return word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private int findCharOffsetAfterTokens(String text, int tokenCount) {
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                if (!inWord) {
                    inWord = true;
                    count++;
                }
            } else {
                if (inWord) {
                    inWord = false;
                    if (count == tokenCount) {
                        return i;
                    }
                }
            }
        }
        return text.length();
    }

    private boolean canMergeCrossPage(CleanedBlock prev, CleanedBlock next) {
        // Only merge across consecutive or incremented page boundaries
        if (prev.pageNumber() >= next.pageNumber()) {
            return false;
        }
        // Never merge headings
        if (prev.isHeading() || next.isHeading()) {
            return false;
        }

        String prevText = prev.text().trim();
        String nextText = next.text().trim();
        if (prevText.isEmpty() || nextText.isEmpty()) {
            return false;
        }

        char lastChar = prevText.charAt(prevText.length() - 1);
        char firstChar = nextText.charAt(0);

        // Does previous block end without sentence-ending punctuation?
        boolean prevIncomplete = (lastChar != '.' && lastChar != '!' && lastChar != '?'
                && lastChar != ':' && lastChar != '”' && lastChar != '"');

        // Does next block begin with a lowercase letter or continuation?
        boolean nextContinues = Character.isLowerCase(firstChar);

        // Previous ends with a comma, hyphen, or conjunction/preposition
        boolean prevTrailingCommaOrConjunction = lastChar == ',' || lastChar == '-'
                || prevText.endsWith(" and") || prevText.endsWith(" a")
        		|| prevText.endsWith(" the") || prevText.endsWith(" to")
                || prevText.endsWith(" with") || prevText.endsWith(" in");

        return prevIncomplete && (nextContinues || prevTrailingCommaOrConjunction);
    }
}
