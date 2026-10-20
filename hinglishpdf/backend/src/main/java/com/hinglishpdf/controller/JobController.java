package com.hinglishpdf.controller;

import com.hinglishpdf.dto.JobResponse;
import com.hinglishpdf.dto.ProgressResponse;
import com.hinglishpdf.entity.Job;
import com.hinglishpdf.entity.JobStatus;
import com.hinglishpdf.repository.JobRepository;
import com.hinglishpdf.service.JobProcessingService;
import com.hinglishpdf.service.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import com.hinglishpdf.entity.JobChunk;
import com.hinglishpdf.repository.JobChunkRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final JobChunkRepository jobChunkRepository;
    private final StorageService storageService;
    private final JobProcessingService jobProcessingService;

    public JobController(
            JobRepository jobRepository,
            JobChunkRepository jobChunkRepository,
            StorageService storageService,
            JobProcessingService jobProcessingService
    ) {
        this.jobRepository = jobRepository;
        this.jobChunkRepository = jobChunkRepository;
        this.storageService = storageService;
        this.jobProcessingService = jobProcessingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createJob(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "hinglish_level", defaultValue = "2") Integer hinglishLevel
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "File is empty."));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Only PDF files are supported."));
        }

        if (file.getSize() > 20 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("detail", "File exceeds maximum size of 20 MB."));
        }

        if (hinglishLevel == null || hinglishLevel < 1 || hinglishLevel > 3) {
            hinglishLevel = 2;
        }

        try {
            UUID jobId = UUID.randomUUID();

            // Save uploaded file to disk
            Path savedPath = storageService.saveUploadedFile(jobId, file);

            Job job = new Job();
            job.setId(jobId);
            job.setOriginalFilename(originalFilename);
            job.setFilePath(savedPath.toAbsolutePath().toString());
            job.setFileSizeBytes(file.getSize());
            job.setHinglishLevel(hinglishLevel);
            job.setStatus(JobStatus.QUEUED);

            // Save job entity
            job = jobRepository.save(job);

            // Kick off async pipeline
            jobProcessingService.processJobAsync(job.getId());

            return ResponseEntity.ok(new JobResponse(
                    job.getId(),
                    job.getStatus().name(),
                    "Job queued successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "Failed to create job: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJobStatus(@PathVariable("id") UUID id) {
        return jobRepository.findById(id)
                .map(job -> {
                    int total = job.getTotalChunks() != null ? job.getTotalChunks() : 0;
                    int done = job.getCompletedChunks() != null ? job.getCompletedChunks() : 0;
                    int percent = 0;

                    if (job.getStatus() == JobStatus.DONE) {
                        percent = 100;
                    } else if (job.getStatus() == JobStatus.QUEUED) {
                        percent = 5;
                    } else if (job.getStatus() == JobStatus.EXTRACTING) {
                        percent = 20;
                    } else if (total > 0) {
                        percent = (int) Math.min(95, Math.round(((double) done / total) * 70.0) + 20);
                    }

                    ProgressResponse.ProgressInfo progress = new ProgressResponse.ProgressInfo(done, total, percent);
                    ProgressResponse.TokenInfo tokens = new ProgressResponse.TokenInfo(
                            job.getTotalInputTokens() != null ? job.getTotalInputTokens() : 0L,
                            job.getTotalOutputTokens() != null ? job.getTotalOutputTokens() : 0L
                    );

                    return ResponseEntity.ok(new ProgressResponse(
                            job.getId(),
                            job.getStatus().name(),
                            job.getOriginalFilename(),
                            job.getPageCount(),
                            job.getHinglishLevel(),
                            job.getIsMockOutput(),
                            progress,
                            tokens,
                            job.getErrorMessage()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadResult(@PathVariable("id") UUID id) {
        Job job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() != JobStatus.DONE || job.getOutputFilePath() == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Job is not completed yet or output file is missing."));
        }

        File file = storageService.getFile(job.getOutputFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String downloadFilename = "hinglish_" + job.getOriginalFilename();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFilename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.length())
                .body(resource);
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<?> getJobChunks(@PathVariable("id") UUID id) {
        List<JobChunk> chunks = jobChunkRepository.findByJobIdOrderByChunkIndexAsc(id);
        return ResponseEntity.ok(chunks.stream().map(c -> Map.of(
                "index", c.getChunkIndex(),
                "sourceText", c.getSourceText() != null ? c.getSourceText() : "",
                "translatedText", c.getTranslatedText() != null ? c.getTranslatedText() : "",
                "status", c.getStatus().name()
        )).toList());
    }
}
