package com.migration.service;

import com.migration.config.AwsConfig;
import com.migration.config.GitHubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpringBatchCodeGenerationService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final S3Client s3Client;
    private final AwsConfig.AwsProperties awsProperties;
    private final GitHubConfig gitHubConfig;
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

    private String invokeBedrock(String systemPrompt, String userPrompt) {
        log.debug("Invoking Bedrock with model: {}", awsProperties.getBedrock().getModelId());

        ConverseRequest converseRequest = ConverseRequest.builder()
                .modelId(awsProperties.getBedrock().getModelId())
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.builder().text(userPrompt).build())
                        .build())
                .build();

        ConverseResponse response = bedrockRuntimeClient.converse(converseRequest);
        return response.output().message().content().get(0).text();
    }
}
