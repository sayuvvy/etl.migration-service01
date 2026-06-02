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

    @Value("${file.upload.temp-dir:#{systemProperties['java.io.tmpdir']}}")
    private String tempDirectory;

    public Path uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        log.info("Processing uploaded file: {}", originalFilename);

        if (originalFilename == null ||
                (!originalFilename.endsWith(".zip") && !originalFilename.endsWith(".xml"))) {
            throw new IllegalArgumentException("Only ZIP and XML files are supported");
        }

        Path tempDir = Paths.get(tempDirectory);
        Files.createDirectories(tempDir);

        String uniqueFileName = UUID.randomUUID() + "-" + originalFilename;
        Path uploadPath = tempDir.resolve(uniqueFileName);

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, uploadPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("File uploaded to temp path: {}", uploadPath);

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
