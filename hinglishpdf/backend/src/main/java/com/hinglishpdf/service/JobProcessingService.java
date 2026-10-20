package com.hinglishpdf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hinglishpdf.dto.ExtractorResponse;
import com.hinglishpdf.entity.Job;
import com.hinglishpdf.entity.JobChunk;
import com.hinglishpdf.entity.JobStatus;
import com.hinglishpdf.llm.LlmProvider;
import com.hinglishpdf.repository.JobChunkRepository;
import com.hinglishpdf.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

@Service
public class JobProcessingService {

    private static final Logger log = LoggerFactory.getLogger(JobProcessingService.class);

    private final JobRepository jobRepository;
    private final JobChunkRepository jobChunkRepository;
    private final ExtractorClient extractorClient;
    private final ScannedCheckService scannedCheckService;
    private final TextCleanerService textCleanerService;
    private final ChunkerService chunkerService;
    private final LlmProvider llmProvider;
    private final TranslationValidatorService translationValidatorService;
    private final PdfGeneratorService pdfGeneratorService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final int maxConcurrentChunks;
    private final int maxRetries;
    private final ExecutorService chunkExecutor;

    public JobProcessingService(
            JobRepository jobRepository,
            JobChunkRepository jobChunkRepository,
            ExtractorClient extractorClient,
            ScannedCheckService scannedCheckService,
            TextCleanerService textCleanerService,
            ChunkerService chunkerService,
            LlmProvider llmProvider,
            TranslationValidatorService translationValidatorService,
            PdfGeneratorService pdfGeneratorService,
            StorageService storageService,
            @Value("${app.max-concurrent-chunks:5}") int maxConcurrentChunks,
            @Value("${app.max-retries:3}") int maxRetries
    ) {
        this.jobRepository = jobRepository;
        this.jobChunkRepository = jobChunkRepository;
        this.extractorClient = extractorClient;
        this.scannedCheckService = scannedCheckService;
        this.textCleanerService = textCleanerService;
        this.chunkerService = chunkerService;
        this.llmProvider = llmProvider;
        this.translationValidatorService = translationValidatorService;
        this.pdfGeneratorService = pdfGeneratorService;
        this.storageService = storageService;
        this.maxConcurrentChunks = maxConcurrentChunks;
        this.maxRetries = maxRetries;
        this.chunkExecutor = Executors.newFixedThreadPool(maxConcurrentChunks);
    }

    /**
     * Requirement: On app startup, resume jobs stuck in TRANSLATING.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeStuckJobsOnStartup() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Job> stuckJobs = jobRepository.findByStatusIn(List.of(JobStatus.TRANSLATING, JobStatus.EXTRACTING));
                if (stuckJobs.isEmpty()) return;

                log.info("Found {} stuck jobs on startup. Resuming processing...", stuckJobs.size());
                for (Job job : stuckJobs) {
                    processJob(job.getId());
                }
            } catch (Exception e) {
                log.error("Error resuming stuck jobs on startup", e);
            }
        }, chunkExecutor);
    }

    @Async
    public void processJobAsync(UUID jobId) {
        processJob(jobId);
    }

    public void processJob(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job not found for ID: {}", jobId);
            return;
        }

        try {
            // Check if job already has chunks created (resuming translation)
            List<JobChunk> existingChunks = jobChunkRepository.findByJobIdOrderByChunkIndexAsc(jobId);

            if (existingChunks.isEmpty()) {
                // 1. Mark status as EXTRACTING
                job.setStatus(JobStatus.EXTRACTING);
                jobRepository.save(job);

                // 2. Extract text via Python FastAPI service
                log.info("Extracting text for job: {}", jobId);
                ExtractorResponse extracted = extractorClient.extractText(Paths.get(job.getFilePath()));

                job.setPageCount(extracted.pageCount());
                job.setAvgCharsPerPage(extracted.avgCharsPerPage());

                // 3. Scanned check
                if (scannedCheckService.isScanned(extracted)) {
                    log.warn("Job {} failed scanned check: avgCharsPerPage={}", jobId, extracted.avgCharsPerPage());
                    job.setStatus(JobStatus.FAILED);
                    job.setErrorMessage(ScannedCheckService.SCANNED_ERROR_MESSAGE);
                    jobRepository.save(job);
                    return;
                }

                // 4. Clean text
                List<TextCleanerService.CleanedBlock> cleaned = textCleanerService.clean(extracted);

                // 5. Chunk text (800 - 1200 tokens)
                List<ChunkerService.Chunk> chunkList = chunkerService.chunk(cleaned);
                if (chunkList.isEmpty()) {
                    job.setStatus(JobStatus.FAILED);
                    job.setErrorMessage("No translatable content found in the PDF.");
                    jobRepository.save(job);
                    return;
                }

                // 6. Save chunks in DB
                List<JobChunk> savedChunks = new ArrayList<>();
                for (int i = 0; i < chunkList.size(); i++) {
                    ChunkerService.Chunk c = chunkList.get(i);
                    JobChunk jc = new JobChunk();
                    jc.setJobId(jobId);
                    jc.setChunkIndex(i);
                    jc.setStatus(JobStatus.PENDING);
                    jc.setIsHeading(c.isHeading());
                    jc.setSourceText(c.text());
                    jc.setInputTokens(c.estimatedTokens());
                    savedChunks.add(jobChunkRepository.save(jc));
                }

                job.setTotalChunks(savedChunks.size());
                job.setCompletedChunks(0);
                job.setStatus(JobStatus.TRANSLATING);
                jobRepository.save(job);
                existingChunks = savedChunks;

                // 7. Extract document glossary (headings + sample text)
                List<String> headings = cleaned.stream()
                        .filter(TextCleanerService.CleanedBlock::isHeading)
                        .map(TextCleanerService.CleanedBlock::text)
                        .toList();
                String sampleText = cleaned.stream()
                        .limit(5)
                        .map(TextCleanerService.CleanedBlock::text)
                        .reduce("", (a, b) -> a + "\n" + b);

                List<String> glossary = llmProvider.extractGlossary(headings, sampleText);
                job.setGlossaryJson(objectMapper.writeValueAsString(glossary));
                jobRepository.save(job);
            }

            // 8. Translate chunks in parallel (max 5 at a time) with retries
            log.info("Starting chunk translation for job {}: {} total chunks", jobId, existingChunks.size());
            job.setStatus(JobStatus.TRANSLATING);
            jobRepository.save(job);

            List<String> glossary = Collections.emptyList();
            if (job.getGlossaryJson() != null) {
                glossary = objectMapper.readValue(job.getGlossaryJson(), new TypeReference<>() {});
            }

            translateChunksParallel(job, existingChunks, glossary);

            // Verify all chunks completed
            long failedCount = jobChunkRepository.countByJobIdAndStatus(jobId, JobStatus.FAILED);
            if (failedCount > 0) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("Some chunks failed after max retries. You can retry the job to resume.");
                jobRepository.save(job);
                return;
            }

            // 9. Generate clean Hinglish PDF
            job.setStatus(JobStatus.GENERATING_PDF);
            job.setIsMockOutput(llmProvider.isMock());
            jobRepository.save(job);

            List<JobChunk> finalChunks = jobChunkRepository.findByJobIdOrderByChunkIndexAsc(jobId);
            Path outputPath = storageService.getOutputPath(jobId);
            pdfGeneratorService.generateHinglishPdf(job.getOriginalFilename(), finalChunks, outputPath, llmProvider.isMock());

            job.setOutputFilePath(outputPath.toAbsolutePath().toString());
            job.setStatus(JobStatus.DONE);
            jobRepository.save(job);
            log.info("Job {} completed successfully! Output: {}", jobId, outputPath);

        } catch (Exception e) {
            log.error("Fatal error processing job {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Processing error: " + e.getMessage());
            jobRepository.save(job);
        }
    }

    private void translateChunksParallel(Job job, List<JobChunk> chunks, List<String> glossary) {
        Semaphore semaphore = new Semaphore(maxConcurrentChunks);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            JobChunk chunk = chunks.get(i);
            if (chunk.getStatus() == JobStatus.DONE) {
                continue; // Skip already completed chunks (resumability)
            }

            // Provide previous chunk text for style continuity
            String prevContext = "";
            if (i > 0) {
                JobChunk prev = chunks.get(i - 1);
                prevContext = prev.getTranslatedText() != null ? prev.getTranslatedText() : prev.getSourceText();
            }

            final String context = prevContext;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    translateChunkWithRetries(job, chunk, glossary, context);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();
                }
            }, chunkExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void translateChunkWithRetries(Job job, JobChunk chunk, List<String> glossary, String previousContext) {
        chunk.setStatus(JobStatus.TRANSLATING);
        jobChunkRepository.save(chunk);

        int attempts = 0;
        boolean success = false;

        while (attempts < maxRetries && !success) {
            attempts++;
            try {
                String translated = llmProvider.translateChunk(
                        chunk.getSourceText(),
                        job.getHinglishLevel(),
                        glossary,
                        previousContext
                );

                // Clean and normalize translated text (stuttering, boilerplate, quotes)
                translated = translationValidatorService.cleanAndNormalize(translated);

                // Perform quality validation (number preservation, meaning, mixed-language leftovers, broken sentences)
                TranslationValidatorService.ValidationReport report = translationValidatorService.validate(chunk.getSourceText(), translated);
                if (!report.isValid()) {
                    log.warn("Chunk {} validation warnings (attempt {}/{}): {}", chunk.getChunkIndex(), attempts, maxRetries, report.issues());
                    if (report.hasSevereIssues() && attempts < maxRetries) {
                        log.warn("Chunk {} severe issues detected (e.g. untranslated English leftovers or severe omission). Retrying...", chunk.getChunkIndex());
                        continue;
                    }
                }

                int outputTokens = (int) Math.ceil((double) translated.length() / 4.0);

                chunk.setTranslatedText(translated);
                chunk.setOutputTokens(outputTokens);
                chunk.setStatus(JobStatus.DONE);
                chunk.setRetryCount(attempts - 1);
                chunk.setErrorMessage(null);
                jobChunkRepository.save(chunk);

                // Increment progress atomically on job
                synchronized (this) {
                    Job current = jobRepository.findById(job.getId()).orElse(job);
                    current.setCompletedChunks((current.getCompletedChunks() != null ? current.getCompletedChunks() : 0) + 1);
                    current.setTotalInputTokens((current.getTotalInputTokens() != null ? current.getTotalInputTokens() : 0) + chunk.getInputTokens());
                    current.setTotalOutputTokens((current.getTotalOutputTokens() != null ? current.getTotalOutputTokens() : 0) + outputTokens);
                    
                    // Cost estimation: ~$0.15/1M input tokens, ~$0.60/1M output tokens (standard gpt-4o-mini pricing)
                    double cost = (current.getTotalInputTokens() * 0.00000015) + (current.getTotalOutputTokens() * 0.00000060);
                    current.setEstimatedCostUsd(BigDecimal.valueOf(cost));
                    
                    jobRepository.save(current);
                }

                success = true;

            } catch (Exception e) {
                log.warn("Chunk {} failed attempt {}/{}: {}", chunk.getChunkIndex(), attempts, maxRetries, e.getMessage());
                if (attempts >= maxRetries) {
                    chunk.setStatus(JobStatus.FAILED);
                    chunk.setRetryCount(attempts);
                    chunk.setErrorMessage(e.getMessage());
                    jobChunkRepository.save(chunk);
                } else {
                    // If Google API returns 429 / quota limit, back off for 16 seconds
                    long sleepMs = (e.getMessage() != null && (e.getMessage().contains("429") || e.getMessage().contains("Quota") || e.getMessage().contains("RESOURCE_EXHAUSTED")))
                            ? 16_000L
                            : attempts * 1000L;
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }
}
