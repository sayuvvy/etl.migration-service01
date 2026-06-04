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
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.scheduling.annotation.Async;

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

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "[\\w][\\w/\\-]*\\.(?:java|xml|yml|yaml|properties|sql|json|gradle|md|txt)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:java|xml|yaml|yml|properties|sql|json|gradle|bash|)?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE
    );

    // Each phase generates a focused set of files to stay within token limits
    private static final List<String> GENERATION_PHASES = List.of(
            "Generate ONLY pom.xml and src/main/resources/application.yml with complete content.",
            "Generate ONLY src/main/java/com/migration/batch/BatchApplication.java, " +
            "src/main/java/com/migration/batch/config/JobConfig.java and " +
            "src/main/java/com/migration/batch/config/StepConfig.java.",
            "Generate ONLY src/main/java/com/migration/batch/config/DataSourceConfig.java, " +
            "src/main/java/com/migration/batch/config/SchedulerConfig.java and " +
            "src/main/java/com/migration/batch/config/CommonBeanConfig.java.",
            "Generate ONLY src/main/java/com/migration/batch/config/InterceptorConfig.java, " +
            "the model package POJOs under src/main/java/com/migration/batch/model/ and " +
            "src/main/java/com/migration/batch/mapping/RowMapper.java.",
            "Generate ONLY the reader package under src/main/java/com/migration/batch/reader/ " +
            "and processor package under src/main/java/com/migration/batch/processor/.",
            "Generate ONLY the writer package under src/main/java/com/migration/batch/writer/, " +
            "tasklet package under src/main/java/com/migration/batch/tasklet/ and " +
            "src/main/java/com/migration/batch/policy/SkipAndRetryPolicy.java.",
            "Generate ONLY the listener package under src/main/java/com/migration/batch/listener/ " +
            "(JobLoggingListener, StepLoggingListener, ChunkLoggingListener) and " +
            "src/main/java/com/migration/batch/interceptor/StepExecutionListenerImpl.java."
    );

    @Async("codeGenerationExecutor")
    public void generateSpringBatchCodeAsync(String brsS3Path, String executionId, String repoName) {
        try {
            executionStatusService.setProcessing(executionId);
            String repoUrl = generateSpringBatchCode(brsS3Path, executionId, repoName);
            executionStatusService.setSuccess(executionId, repoUrl);
        } catch (Exception e) {
            log.error("Async code generation failed for execution {}: {}", executionId, e.getMessage(), e);
            executionStatusService.setFailed(executionId, e.getMessage());
        }
    }

    public String generateSpringBatchCode(String brsS3Path, String executionId, String repoName) throws Exception {
        log.info("Starting Spring Batch code generation for execution: {} with BRS: {}", executionId, brsS3Path);

        try {
            String brsContent = readBrsFromS3(brsS3Path);
            String codeGenPrompt = readCodeGenerationPrompt();

            Map<String, String> allFiles = new LinkedHashMap<>();

            for (int i = 0; i < GENERATION_PHASES.size(); i++) {
                log.info("Executing generation phase {}/{}", i + 1, GENERATION_PHASES.size());
                String phaseInstruction = GENERATION_PHASES.get(i);
                String userPrompt = phaseInstruction + "\n\nBased on this BRS:\n\n" + brsContent;

                String generatedCode = invokeBedrock(codeGenPrompt, userPrompt);
                Map<String, String> phaseFiles = parseGeneratedCode(generatedCode);
                allFiles.putAll(phaseFiles);
                log.info("Phase {} complete — {} new files, {} total", i + 1, phaseFiles.size(), allFiles.size());
            }

            log.info("All phases complete. Pushing {} files to GitHub", allFiles.size());
            String repoUrl = gitHubService.pushFilesToGitHub(allFiles, repoName, executionId);

            log.info("Spring Batch code generation completed. Repo URL: {}", repoUrl);
            return repoUrl;

        } catch (Exception e) {
            log.error("Error during Spring Batch code generation for execution {}: {}", executionId, e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, String> parseGeneratedCode(String generatedCode) {
        Map<String, String> files = new LinkedHashMap<>();

        Matcher blockMatcher = CODE_BLOCK_PATTERN.matcher(generatedCode);
        while (blockMatcher.find()) {
            String codeContent = blockMatcher.group(1);
            if (codeContent == null || codeContent.isBlank()) continue;

            int lookbackStart = Math.max(0, blockMatcher.start() - 400);
            String preceding = generatedCode.substring(lookbackStart, blockMatcher.start());

            String filePath = null;
            Matcher pathMatcher = FILE_PATH_PATTERN.matcher(preceding);
            while (pathMatcher.find()) {
                filePath = pathMatcher.group();
            }

            if (filePath != null && !files.containsKey(filePath)) {
                files.put(filePath, codeContent);
                log.debug("Parsed file: {}", filePath);
            }
        }

        return files;
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
        log.debug("Invoking Bedrock with model: {}", awsProperties.getBedrock().getModelId());

        ConverseRequest converseRequest = ConverseRequest.builder()
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

        ConverseResponse response = bedrockRuntimeClient.converse(converseRequest);
        return response.output().message().content().get(0).text();
    }
}
