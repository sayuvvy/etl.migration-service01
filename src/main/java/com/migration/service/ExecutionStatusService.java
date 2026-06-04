package com.migration.service;

import com.migration.dto.MigrationResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ExecutionStatusService {

    private final ConcurrentHashMap<String, MigrationResponse> statusMap     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>>      generatedFiles = new ConcurrentHashMap<>();

    public void setProcessing(String executionId, int totalFilesExpected) {
        generatedFiles.put(executionId, new CopyOnWriteArrayList<>());
        statusMap.put(executionId, MigrationResponse.builder()
                .status("PROCESSING")
                .message("Spring Batch code generation in progress")
                .executionId(executionId)
                .timestamp(now())
                .generatedFiles(new ArrayList<>())
                .filesGeneratedCount(0)
                .totalFilesExpected(totalFilesExpected)
                .build());
    }

    public void addGeneratedFile(String executionId, String filePath) {
        List<String> files = generatedFiles.get(executionId);
        if (files == null) return;
        files.add(filePath);

        MigrationResponse current = statusMap.get(executionId);
        if (current == null) return;

        statusMap.put(executionId, MigrationResponse.builder()
                .status("PROCESSING")
                .message(String.format("Generating files... %d/%d complete",
                        files.size(), current.getTotalFilesExpected()))
                .executionId(executionId)
                .timestamp(now())
                .generatedFiles(new ArrayList<>(files))
                .filesGeneratedCount(files.size())
                .totalFilesExpected(current.getTotalFilesExpected())
                .build());
    }

    public void setSuccess(String executionId, String repoUrl) {
        List<String> files = generatedFiles.getOrDefault(executionId, new ArrayList<>());
        statusMap.put(executionId, MigrationResponse.builder()
                .status("SUCCESS")
                .message("Spring Batch code generated and pushed to GitHub")
                .springBatchRepoUrl(repoUrl)
                .executionId(executionId)
                .timestamp(now())
                .generatedFiles(new ArrayList<>(files))
                .filesGeneratedCount(files.size())
                .totalFilesExpected(files.size())
                .build());
    }

    public void setFailed(String executionId, String errorDetails) {
        List<String> files = generatedFiles.getOrDefault(executionId, new ArrayList<>());
        statusMap.put(executionId, MigrationResponse.builder()
                .status("FAILED")
                .message("Code generation failed")
                .errorDetails(errorDetails)
                .executionId(executionId)
                .timestamp(now())
                .generatedFiles(new ArrayList<>(files))
                .filesGeneratedCount(files.size())
                .build());
    }

    public Optional<MigrationResponse> getStatus(String executionId) {
        return Optional.ofNullable(statusMap.get(executionId));
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
