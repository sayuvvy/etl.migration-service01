package com.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.AwsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrsGenerationService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final S3Client s3Client;
    private final AwsConfig.AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final FileProcessingService fileProcessingService;

    public String generateBrs(Path zipFilePath, String executionId) throws Exception {
        log.info("Starting BRS generation for execution: {}", executionId);
        
        try {
            // Extract and read ZIP file contents
            String zipContents = extractZipContents(zipFilePath);
            log.debug("ZIP file extracted successfully. Content length: {}", zipContents.length());
            
            // Read the BRD prompt from resources
            String brdPrompt = readBrdPrompt();
            
            // Prepare Bedrock request
            String systemPrompt = brdPrompt;
            String userPrompt = "Based on the following Informatica IDMC export, generate a comprehensive Business Requirements Document (BRD) in Markdown format:\n\n" + zipContents;
            
            // Call Bedrock Claude model
            String brsContent = invokeBedrock(systemPrompt, userPrompt);
            
            // Upload BRS to S3
            String s3Path = uploadBrsToS3(brsContent, executionId);
            
            log.info("BRS generation completed successfully. S3 path: {}", s3Path);
            return s3Path;
            
        } catch (Exception e) {
            log.error("Error during BRS generation for execution {}: {}", executionId, e.getMessage(), e);
            throw e;
        }
    }

    private String extractZipContents(Path zipFilePath) throws IOException {
        log.info("Extracting ZIP file: {}", zipFilePath);
        StringBuilder contents = new StringBuilder();
        
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            zipFile.stream()
                    .filter(entry -> entry.getName().endsWith(".json"))
                    .forEach(entry -> {
                        try {
                            String content = new String(
                                    zipFile.getInputStream(entry).readAllBytes(),
                                    StandardCharsets.UTF_8
                            );
                            contents.append("\n--- File: ").append(entry.getName()).append(" ---\n");
                            contents.append(content).append("\n");
                        } catch (IOException e) {
                            log.warn("Could not read entry {}: {}", entry.getName(), e.getMessage());
                        }
                    });
        }
        
        return contents.toString();
    }

    private String readBrdPrompt() throws IOException {
        // Read from resources/prompts/brd-prompt.txt
        ClassLoader classLoader = getClass().getClassLoader();
        String prompt = new String(
                classLoader.getResourceAsStream("prompts/brd-prompt.txt").readAllBytes(),
                StandardCharsets.UTF_8
        );
        return prompt;
    }

    private String invokeBedrock(String systemPrompt, String userPrompt) throws Exception {
        log.debug("Invoking Bedrock with model: {}", awsProperties.getBedrock().getModelId());
        
        Map<String, Object> request = new HashMap<>();
        request.put("anthropic_version", "bedrock-2023-06-01");
        request.put("max_tokens", 4000);
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

    private String uploadBrsToS3(String brsContent, String executionId) throws Exception {
        log.info("Uploading BRS to S3 for execution: {}", executionId);
        
        String fileName = String.format("brs-%s-%s.md", executionId, UUID.randomUUID().toString().substring(0, 8));
        String key = awsProperties.getS3().getBrsPath() + fileName;
        
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsProperties.getS3().getBucketName())
                .key(key)
                .build();
        
        PutObjectResponse response = s3Client.putObject(
                putObjectRequest,
                RequestBody.fromString(brsContent, StandardCharsets.UTF_8)
        );
        
        log.info("BRS uploaded to S3: s3://{}/{}", awsProperties.getS3().getBucketName(), key);
        return "s3://" + awsProperties.getS3().getBucketName() + "/" + key;
    }
}
