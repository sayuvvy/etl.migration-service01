package com.migration.controller;

import com.migration.dto.MigrationResponse;
import com.migration.service.BrsGenerationService;
import com.migration.service.FileProcessingService;
import com.migration.service.SpringBatchCodeGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationControllerTest {

    @Mock
    private BrsGenerationService brsGenerationService;

    @Mock
    private SpringBatchCodeGenerationService codeGenerationService;

    @Mock
    private FileProcessingService fileProcessingService;

    @InjectMocks
    private MigrationController migrationController;

    private MultipartFile mockZipFile;

    @BeforeEach
    void setUp() {
        mockZipFile = new MockMultipartFile(
                "file",
                "test.zip",
                "application/zip",
                "test zip content".getBytes()
        );
    }

    @Test
    void testGenerateBrsSuccess() throws Exception {
        // Arrange
        Path tempPath = Paths.get("/tmp/test.zip");
        String expectedS3Path = "s3://bucket/brs-test.md";

        when(fileProcessingService.uploadZipFile(any(MultipartFile.class)))
                .thenReturn(tempPath);
        when(brsGenerationService.generateBrs(any(Path.class), anyString()))
                .thenReturn(expectedS3Path);

        // Act
        ResponseEntity<MigrationResponse> response = migrationController.generateBrs(mockZipFile);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertEquals(expectedS3Path, response.getBody().getBrsFilePath());
    }

    @Test
    void testGenerateBrsInvalidFile() throws Exception {
        // Arrange
        MultipartFile invalidFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "test content".getBytes()
        );

        when(fileProcessingService.uploadZipFile(any(MultipartFile.class)))
                .thenThrow(new IllegalArgumentException("Only ZIP files are supported"));

        // Act
        ResponseEntity<MigrationResponse> response = migrationController.generateBrs(invalidFile);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
    }

    @Test
    void testGenerateCodeSuccess() throws Exception {
        // Arrange
        String brsS3Path = "s3://bucket/brs-test.md";
        String repoName = "spring-batch-migration";
        String expectedRepoUrl = "https://github.com/user/spring-batch-migration-20240602120000";

        when(codeGenerationService.generateSpringBatchCode(anyString(), anyString(), anyString()))
                .thenReturn(expectedRepoUrl);

        // Act
        ResponseEntity<MigrationResponse> response = migrationController.generateSpringBatchCode(brsS3Path, repoName);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertEquals(expectedRepoUrl, response.getBody().getSpringBatchRepoUrl());
    }

    @Test
    void testGenerateCodeFailure() throws Exception {
        // Arrange
        String brsS3Path = "s3://bucket/invalid.md";
        String repoName = "spring-batch-migration";

        when(codeGenerationService.generateSpringBatchCode(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Failed to generate code"));

        // Act
        ResponseEntity<MigrationResponse> response = migrationController.generateSpringBatchCode(brsS3Path, repoName);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
    }
}
