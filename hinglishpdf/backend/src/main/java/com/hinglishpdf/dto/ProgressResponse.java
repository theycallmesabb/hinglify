package com.hinglishpdf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ProgressResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("status") String status,
    @JsonProperty("original_filename") String originalFilename,
    @JsonProperty("page_count") Integer pageCount,
    @JsonProperty("hinglish_level") Integer hinglishLevel,
    @JsonProperty("is_mock") Boolean isMock,
    @JsonProperty("progress") ProgressInfo progress,
    @JsonProperty("tokens") TokenInfo tokens,
    @JsonProperty("error") String error
) {
    public record ProgressInfo(
        @JsonProperty("chunks_done") int chunksDone,
        @JsonProperty("total_chunks") int totalChunks,
        @JsonProperty("percent") int percent
    ) {}

    public record TokenInfo(
        @JsonProperty("in") long in,
        @JsonProperty("out") long out
    ) {}
}
