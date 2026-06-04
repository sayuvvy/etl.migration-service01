package com.migration.service;

import com.migration.dto.MigrationResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExecutionStatusService {

    private final ConcurrentHashMap<String, MigrationResponse> statusMap = new ConcurrentHashMap<>();

    public void setProcessing(String executionId) {
        statusMap.put(executionId, MigrationResponse.builder()
                .status("PROCESSING")
                .message("Spring Batch code generation in progress")
                .executionId(executionId)
                .timestamp(now())
                .build());
    }

    public void setSuccess(String executionId, String repoUrl) {
        statusMap.put(executionId, MigrationResponse.builder()
                .status("SUCCESS")
                .message("Spring Batch code generated and pushed to GitHub")
                .springBatchRepoUrl(repoUrl)
                .executionId(executionId)
                .timestamp(now())
                .build());
    }

    public void setFailed(String executionId, String errorDetails) {
        statusMap.put(executionId, MigrationResponse.builder()
                .status("FAILED")
                .message("Code generation failed")
                .errorDetails(errorDetails)
                .executionId(executionId)
                .timestamp(now())
                .build());
    }

    public Optional<MigrationResponse> getStatus(String executionId) {
        return Optional.ofNullable(statusMap.get(executionId));
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
