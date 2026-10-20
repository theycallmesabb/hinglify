package com.hinglishpdf.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractorResponse(
    @JsonProperty("page_count") int pageCount,
    @JsonProperty("total_chars") int totalChars,
    @JsonProperty("avg_chars_per_page") double avgCharsPerPage,
    @JsonProperty("pages") List<PageInfo> pages
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfo(
        @JsonProperty("page_number") int pageNumber,
        @JsonProperty("page_width") Double pageWidth,
        @JsonProperty("page_height") Double pageHeight,
        @JsonProperty("total_chars") int totalChars,
        @JsonProperty("blocks") List<BlockInfo> blocks
    ) {
        public PageInfo(int pageNumber, int totalChars, List<BlockInfo> blocks) {
            this(pageNumber, null, null, totalChars, blocks);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlockInfo(
        @JsonProperty("text") String text,
        @JsonProperty("font_size") double fontSize,
        @JsonProperty("bbox") List<Double> bbox,
        @JsonProperty("is_heading") boolean isHeading,
        @JsonProperty("is_header_footer") Boolean isHeaderFooter
    ) {
        public BlockInfo(String text, double fontSize, List<Double> bbox, boolean isHeading) {
            this(text, fontSize, bbox, isHeading, false);
        }
    }
}
