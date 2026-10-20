package com.hinglishpdf.repository;

import com.hinglishpdf.entity.Job;
import com.hinglishpdf.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    List<JobStatus> findByStatus(JobStatus status);
    List<Job> findByStatusIn(List<JobStatus> statuses);
}
