package com.migration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class FileProcessingService {

    @Value("${file.upload.temp-dir:./temp}")
    private String tempDirectory;

    public Path uploadZipFile(MultipartFile file) throws IOException {
        log.info("Processing uploaded ZIP file: {}", file.getOriginalFilename());
        
        // Validate file type
        if (!file.getOriginalFilename().endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP files are supported");
        }
        
        // Create temp directory if not exists
        Path tempDir = Paths.get(tempDirectory);
        Files.createDirectories(tempDir);
        
        // Generate unique filename
        String uniqueFileName = UUID.randomUUID() + "-" + file.getOriginalFilename();
        Path uploadPath = tempDir.resolve(uniqueFileName);
        
        // Save file
        file.transferTo(uploadPath.toFile());
        log.info("ZIP file uploaded successfully: {}", uploadPath);
        
        return uploadPath;
    }

    public String readBrsFromS3(String s3Path) throws IOException {
        // This would be implemented to read from S3
        // For now, returning placeholder
        log.debug("Reading BRS from S3: {}", s3Path);
        return "";
    }

    public void cleanupTempFile(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("Cleaned up temp file: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Could not delete temp file {}: {}", filePath, e.getMessage());
        }
    }

    public Path createTempDirectory(String prefix) throws IOException {
        Path tempDir = Paths.get(tempDirectory);
        Files.createDirectories(tempDir);
        
        Path newDir = Files.createTempDirectory(tempDir, prefix);
        log.debug("Created temp directory: {}", newDir);
        return newDir;
    }
}
