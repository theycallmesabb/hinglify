package com.hinglishpdf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record JobResponse(
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("status") String status,
    @JsonProperty("message") String message
) {}
