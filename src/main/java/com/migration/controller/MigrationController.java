package com.migration.controller;

import com.migration.dto.MigrationResponse;
import com.migration.service.BrsGenerationService;
import com.migration.service.FileProcessingService;
import com.migration.service.SpringBatchCodeGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/migrate")
@RequiredArgsConstructor
@Slf4j
public class MigrationController {

    private final BrsGenerationService brsGenerationService;
    private final SpringBatchCodeGenerationService codeGenerationService;
    private final FileProcessingService fileProcessingService;

    @PostMapping("/generate-brs")
    public ResponseEntity<MigrationResponse> generateBrs(
            @RequestParam("file") MultipartFile zipFile) {
        
        String executionId = UUID.randomUUID().toString();
        log.info("BRS generation request received. Execution ID: {}, File: {}", executionId, zipFile.getOriginalFilename());
        
        try {
            // Upload and process ZIP file
            Path uploadedZipPath = fileProcessingService.uploadFile(zipFile);
            
            // Generate BRS
            String brsS3Path = brsGenerationService.generateBrs(uploadedZipPath, executionId);
            
            // Cleanup temp file
            fileProcessingService.cleanupTempFile(uploadedZipPath);
            
            MigrationResponse response = MigrationResponse.builder()
                    .status("SUCCESS")
                    .message("BRS generated successfully")
                    .brsFilePath(brsS3Path)
                    .executionId(executionId)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .build();
            
            log.info("BRS generation completed successfully. Response: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for BRS generation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    MigrationResponse.builder()
                            .status("FAILED")
                            .message("Invalid input")
                            .errorDetails(e.getMessage())
                            .executionId(executionId)
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                            .build()
            );
            
        } catch (Exception e) {
            log.error("Error during BRS generation for execution {}: {}", executionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    MigrationResponse.builder()
                            .status("FAILED")
                            .message("BRS generation failed")
                            .errorDetails(e.getMessage())
                            .executionId(executionId)
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                            .build()
            );
        }
    }

    @PostMapping("/generate-code")
    public ResponseEntity<MigrationResponse> generateSpringBatchCode(
            @RequestParam("brs_s3_path") String brsS3Path,
            @RequestParam("repo_name") String repoName) {
        
        String executionId = UUID.randomUUID().toString();
        log.info("Spring Batch code generation request received. Execution ID: {}, BRS: {}, Repo: {}", 
                executionId, brsS3Path, repoName);
        
        try {
            // Generate Spring Batch code
            String repoUrl = codeGenerationService.generateSpringBatchCode(brsS3Path, executionId, repoName);
            
            MigrationResponse response = MigrationResponse.builder()
                    .status("SUCCESS")
                    .message("Spring Batch code generated and pushed to GitHub")
                    .springBatchRepoUrl(repoUrl)
                    .executionId(executionId)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .build();
            
            log.info("Spring Batch code generation completed successfully. Response: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for code generation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    MigrationResponse.builder()
                            .status("FAILED")
                            .message("Invalid input")
                            .errorDetails(e.getMessage())
                            .executionId(executionId)
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                            .build()
            );
            
        } catch (Exception e) {
            log.error("Error during code generation for execution {}: {}", executionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    MigrationResponse.builder()
                            .status("FAILED")
                            .message("Code generation failed")
                            .errorDetails(e.getMessage())
                            .executionId(executionId)
                            .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                            .build()
            );
        }
    }
}
