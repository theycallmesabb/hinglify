package com.hinglishpdf.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private String originalFilename;

    @Column
    private String filePath;

    private String outputFilePath;

    private Integer pageCount;

    private Long fileSizeBytes;

    private Double avgCharsPerPage;

    @Column(nullable = false)
    private Integer hinglishLevel; // 1, 2, or 3

    @Column(columnDefinition = "TEXT")
    private String glossaryJson;

    private Integer totalChunks = 0;

    private Integer completedChunks = 0;

    private Long totalInputTokens = 0L;

    private Long totalOutputTokens = 0L;

    @Column(precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    private Boolean isMockOutput = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Job() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = JobStatus.QUEUED;
        }
        if (hinglishLevel == null) {
            hinglishLevel = 2;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getOutputFilePath() { return outputFilePath; }
    public void setOutputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public Double getAvgCharsPerPage() { return avgCharsPerPage; }
    public void setAvgCharsPerPage(Double avgCharsPerPage) { this.avgCharsPerPage = avgCharsPerPage; }

    public Integer getHinglishLevel() { return hinglishLevel; }
    public void setHinglishLevel(Integer hinglishLevel) { this.hinglishLevel = hinglishLevel; }

    public String getGlossaryJson() { return glossaryJson; }
    public void setGlossaryJson(String glossaryJson) { this.glossaryJson = glossaryJson; }

    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }

    public Integer getCompletedChunks() { return completedChunks; }
    public void setCompletedChunks(Integer completedChunks) { this.completedChunks = completedChunks; }

    public Long getTotalInputTokens() { return totalInputTokens; }
    public void setTotalInputTokens(Long totalInputTokens) { this.totalInputTokens = totalInputTokens; }

    public Long getTotalOutputTokens() { return totalOutputTokens; }
    public void setTotalOutputTokens(Long totalOutputTokens) { this.totalOutputTokens = totalOutputTokens; }

    public BigDecimal getEstimatedCostUsd() { return estimatedCostUsd; }
    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; }

    public Boolean getIsMockOutput() { return isMockOutput; }
    public void setIsMockOutput(Boolean isMockOutput) { this.isMockOutput = isMockOutput; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
