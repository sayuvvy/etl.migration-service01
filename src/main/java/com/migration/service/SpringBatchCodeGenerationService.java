package com.migration.service;

import com.migration.config.AwsConfig;
import com.migration.config.GitHubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpringBatchCodeGenerationService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final S3Client s3Client;
    private final AwsConfig.AwsProperties awsProperties;
    private final GitHubConfig gitHubConfig;
    private final GitHubService gitHubService;
    private final ExecutionStatusService executionStatusService;

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:java|xml|yaml|yml|properties|sql|json|gradle|bash)?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE
    );

    private static final String PKG = "src/main/java/com/migration/batch";

    // One entry per file: [filePath, focused generation instruction]
    private static final List<String[]> GENERATION_FILES = List.of(
        new String[]{"pom.xml",
            "Generate ONLY pom.xml. Use Spring Boot 3.4.0 parent, Spring Batch 5.2.3, Java 21, SQLServer JDBC driver, JaCoCo plugin. Output ONLY the file content in a ```xml block."},
        new String[]{"src/main/resources/application.yml",
            "Generate ONLY application.yml with Spring Batch, SQLServer datasource, Spring Scheduler and logging config. Output ONLY in a ```yaml block."},
        new String[]{PKG + "/BatchApplication.java",
            "Generate ONLY BatchApplication.java with @SpringBootApplication and main method. Package: com.migration.batch. Output ONLY in a ```java block."},
        new String[]{PKG + "/config/JobConfig.java",
            "Generate ONLY JobConfig.java using JobBuilder (NOT JobBuilderFactory). Include JobRepository, TransactionManager, listeners, restartable, idempotent. Package: com.migration.batch.config. Output ONLY in a ```java block."},
        new String[]{PKG + "/config/StepConfig.java",
            "Generate ONLY StepConfig.java using StepBuilder (NOT StepBuilderFactory). Chunk size 1000, skip policy, retry policy, StepScope. Package: com.migration.batch.config. Output ONLY in a ```java block."},
        new String[]{PKG + "/config/DataSourceConfig.java",
            "Generate ONLY DataSourceConfig.java for SQLServer datasource and Spring Batch JobRepository. Package: com.migration.batch.config. Output ONLY in a ```java block."},
        new String[]{PKG + "/config/SchedulerConfig.java",
            "Generate ONLY SchedulerConfig.java using @Scheduled to trigger the batch job. Package: com.migration.batch.config. Output ONLY in a ```java block."},
        new String[]{PKG + "/config/InterceptorConfig.java",
            "Generate ONLY InterceptorConfig.java registering StepExecutionListeners. Package: com.migration.batch.config. Output ONLY in a ```java block."},
        new String[]{PKG + "/config/CommonBeanConfig.java",
            "Generate ONLY CommonBeanConfig.java with common beans such as ObjectMapper and RestTemplate. Package: com.migration.batch.config. Output ONLY in a ```java block."},
        new String[]{PKG + "/model/SourceEntity.java",
            "Generate ONLY SourceEntity.java POJO derived from the BRS source data model. Package: com.migration.batch.model. Output ONLY in a ```java block."},
        new String[]{PKG + "/model/TargetEntity.java",
            "Generate ONLY TargetEntity.java POJO derived from the BRS target data model. Package: com.migration.batch.model. Output ONLY in a ```java block."},
        new String[]{PKG + "/mapping/SourceRowMapper.java",
            "Generate ONLY SourceRowMapper.java implementing RowMapper<SourceEntity> for JDBC. Package: com.migration.batch.mapping. Output ONLY in a ```java block."},
        new String[]{PKG + "/interceptor/StepExecutionListenerImpl.java",
            "Generate ONLY StepExecutionListenerImpl.java implementing StepExecutionListener. Log step start, end, stats. Package: com.migration.batch.interceptor. Output ONLY in a ```java block."},
        new String[]{PKG + "/listener/JobLoggingListener.java",
            "Generate ONLY JobLoggingListener.java implementing JobExecutionListener. Log job start, end, status, duration. Package: com.migration.batch.listener. Output ONLY in a ```java block."},
        new String[]{PKG + "/listener/StepLoggingListener.java",
            "Generate ONLY StepLoggingListener.java implementing StepExecutionListener. Log step metrics. Package: com.migration.batch.listener. Output ONLY in a ```java block."},
        new String[]{PKG + "/listener/ChunkLoggingListener.java",
            "Generate ONLY ChunkLoggingListener.java implementing ChunkListener. Log chunk read/write counts. Package: com.migration.batch.listener. Output ONLY in a ```java block."},
        new String[]{PKG + "/policy/SkipAndRetryPolicy.java",
            "Generate ONLY SkipAndRetryPolicy.java implementing SkipPolicy with retry configuration. Package: com.migration.batch.policy. Output ONLY in a ```java block."},
        new String[]{PKG + "/processor/DataItemProcessor.java",
            "Generate ONLY DataItemProcessor.java implementing ItemProcessor<SourceEntity, TargetEntity>. Include validation, transformation, null for filtered items. Package: com.migration.batch.processor. Output ONLY in a ```java block."},
        new String[]{PKG + "/reader/DataItemReader.java",
            "Generate ONLY DataItemReader.java using JdbcPagingItemReader for SQLServer. Page size 1000. Package: com.migration.batch.reader. Output ONLY in a ```java block."},
        new String[]{PKG + "/tasklet/FileTasklet.java",
            "Generate ONLY FileTasklet.java implementing Tasklet for file move/control logic. Package: com.migration.batch.tasklet. Output ONLY in a ```java block."},
        new String[]{PKG + "/writer/DataItemWriter.java",
            "Generate ONLY DataItemWriter.java using JdbcBatchItemWriter to write TargetEntity to SQLServer. Exactly-once, idempotent. Package: com.migration.batch.writer. Output ONLY in a ```java block."}
    );

    @Async("codeGenerationExecutor")
    public void generateSpringBatchCodeAsync(String brsS3Path, String executionId, String repoName) {
        try {
            executionStatusService.setProcessing(executionId, GENERATION_FILES.size());
            String repoUrl = generateSpringBatchCode(brsS3Path, executionId, repoName);
            executionStatusService.setSuccess(executionId, repoUrl);
        } catch (Exception e) {
            log.error("Async code generation failed for execution {}: {}", executionId, e.getMessage(), e);
            executionStatusService.setFailed(executionId, e.getMessage());
        }
    }

    public String generateSpringBatchCode(String brsS3Path, String executionId, String repoName) throws Exception {
        log.info("Starting Spring Batch code generation for execution: {}", executionId);

        String brsContent     = readBrsFromS3(brsS3Path);
        String codeGenPrompt  = readCodeGenerationPrompt();
        Map<String, String> allFiles = new LinkedHashMap<>();

        for (int i = 0; i < GENERATION_FILES.size(); i++) {
            String filePath    = GENERATION_FILES.get(i)[0];
            String instruction = GENERATION_FILES.get(i)[1];

            log.info("Generating file {}/{}: {}", i + 1, GENERATION_FILES.size(), filePath);

            try {
                String userPrompt = instruction + "\n\nBRS document for context:\n\n" + brsContent;
                String response   = invokeBedrock(codeGenPrompt, userPrompt);
                String content    = extractCodeBlock(response);

                if (!content.isBlank()) {
                    allFiles.put(filePath, content);
                    executionStatusService.addGeneratedFile(executionId, filePath);
                    log.info("Generated: {}", filePath);
                } else {
                    log.warn("No content returned for: {}", filePath);
                }
            } catch (Exception e) {
                log.error("Failed to generate {}: {}", filePath, e.getMessage());
            }
        }

        log.info("All files generated ({} total). Pushing to GitHub.", allFiles.size());
        return gitHubService.pushFilesToGitHub(allFiles, repoName, executionId);
    }

    private String extractCodeBlock(String response) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        return matcher.find() ? matcher.group(1) : response;
    }

    private String readBrsFromS3(String s3Path) throws Exception {
        log.info("Reading BRS from S3: {}", s3Path);
        String[] parts = s3Path.replace("s3://", "").split("/", 2);
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder().bucket(parts[0]).key(parts[1]).build())) {
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
        log.debug("Invoking Bedrock model: {}", awsProperties.getBedrock().getModelId());

        ConverseRequest request = ConverseRequest.builder()
                .modelId(awsProperties.getBedrock().getModelId())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(5120)
                        .build())
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.builder().text(userPrompt).build())
                        .build())
                .build();

        ConverseResponse response = bedrockRuntimeClient.converse(request);
        return response.output().message().content().get(0).text();
    }
}
