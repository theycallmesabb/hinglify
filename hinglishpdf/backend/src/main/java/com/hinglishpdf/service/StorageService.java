package com.hinglishpdf.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class StorageService {

    private final Path uploadDir;
    private final Path outputDir;

    public StorageService(@Value("${app.storage-dir:./storage}") String storageDir) {
        this.uploadDir = Paths.get(storageDir, "uploads");
        this.outputDir = Paths.get(storageDir, "outputs");
        try {
            Files.createDirectories(uploadDir);
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    public Path saveUploadedFile(UUID jobId, MultipartFile file) throws IOException {
        String filename = jobId + "_" + file.getOriginalFilename();
        Path targetPath = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }

    public Path getOutputPath(UUID jobId) {
        return outputDir.resolve(jobId + "_hinglish.pdf");
    }

    public File getFile(String path) {
        return new File(path);
    }
}
