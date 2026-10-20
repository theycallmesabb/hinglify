package com.hinglishpdf.repository;

import com.hinglishpdf.entity.JobChunk;
import com.hinglishpdf.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobChunkRepository extends JpaRepository<JobChunk, UUID> {
    List<JobChunk> findByJobIdOrderByChunkIndexAsc(UUID jobId);
    List<JobChunk> findByJobIdAndStatus(UUID jobId, JobStatus status);
    long countByJobIdAndStatus(UUID jobId, JobStatus status);
}
