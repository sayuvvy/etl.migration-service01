package com.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.AwsConfig;
import com.migration.config.GitHubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpringBatchCodeGenerationService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final S3Client s3Client;
    private final AwsConfig.AwsProperties awsProperties;
    private final GitHubConfig gitHubConfig;
    private final ObjectMapper objectMapper;
    private final GitHubService gitHubService;

    public String generateSpringBatchCode(String brsS3Path, String executionId, String repoName) throws Exception {
        log.info("Starting Spring Batch code generation for execution: {} with BRS: {}", executionId, brsS3Path);
        
        try {
            // Read BRS from S3
            String brsContent = readBrsFromS3(brsS3Path);
            log.debug("BRS content retrieved from S3");
            
            // Read the code generation prompt
            String codeGenPrompt = readCodeGenerationPrompt();
            
            // Prepare Bedrock request
            String systemPrompt = codeGenPrompt;
            String userPrompt = "Based on the following Business Requirements Document, generate a complete Spring Batch 5.2.3 project for Spring Boot 3.4.0:\n\n" + brsContent;
            
            // Call Bedrock Claude model
            String generatedCode = invokeBedrock(systemPrompt, userPrompt);
            
            // Push code to GitHub
            String repoUrl = gitHubService.pushCodeToGitHub(generatedCode, repoName, executionId);
            
            log.info("Spring Batch code generation completed successfully. Repo URL: {}", repoUrl);
            return repoUrl;
            
        } catch (Exception e) {
            log.error("Error during Spring Batch code generation for execution {}: {}", executionId, e.getMessage(), e);
            throw e;
        }
    }

    private String readBrsFromS3(String s3Path) throws Exception {
        log.info("Reading BRS from S3: {}", s3Path);
        
        // Parse S3 path (format: s3://bucket/key)
        String[] parts = s3Path.replace("s3://", "").split("/", 2);
        String bucket = parts[0];
        String key = parts[1];
        
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest)) {
            return new String(response.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readCodeGenerationPrompt() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        return new String(
                classLoader.getResourceAsStream("prompts/code-generation-prompt.txt").readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    private String invokeBedrock(String systemPrompt, String userPrompt) throws Exception {
        log.debug("Invoking Bedrock with model: {}", awsProperties.getBedrock().getModelId());
        
        Map<String, Object> request = new HashMap<>();
        request.put("anthropic_version", "bedrock-2023-06-01");
        request.put("max_tokens", 8000);
        request.put("system", systemPrompt);
        request.put("messages", new Object[]{
                Map.of(
                        "role", "user",
                        "content", userPrompt
                )
        });
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                .modelId(awsProperties.getBedrock().getModelId())
                .body(SdkBytes.fromString(requestJson, StandardCharsets.UTF_8))
                .build();
        
        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(invokeRequest);
        
        String responseBody = response.body().asUtf8String();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        
        Object contentArray = responseMap.get("content");
        if (contentArray instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) contentArray;
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                Map<?, ?> contentMap = (Map<?, ?>) list.get(0);
                return (String) contentMap.get("text");
            }
        }
        
        throw new RuntimeException("Unexpected Bedrock response format");
    }
}
