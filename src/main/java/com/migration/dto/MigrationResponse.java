package com.migration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationResponse {
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("brs_file_path")
    private String brsFilePath;
    
    @JsonProperty("spring_batch_repo_url")
    private String springBatchRepoUrl;
    
    @JsonProperty("execution_id")
    private String executionId;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("error_details")
    private String errorDetails;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class BrsGenerationRequest {
    @JsonProperty("execution_id")
    private String executionId;
    
    @JsonProperty("zip_file_name")
    private String zipFileName;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CodeGenerationRequest {
    @JsonProperty("execution_id")
    private String executionId;
    
    @JsonProperty("brs_file_path")
    private String brsFilePath;
    
    @JsonProperty("repo_name")
    private String repoName;
}
