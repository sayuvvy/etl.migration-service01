package com.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.GitHubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private static final String GITHUB_API = "https://api.github.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // Matches a file path (with common source extensions) in headings, backticks or plain text
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "[\\w][\\w/\\-]*\\.(?:java|xml|yml|yaml|properties|sql|json|gradle|md|txt)",
            Pattern.CASE_INSENSITIVE
    );

    // Matches a markdown code block
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:java|xml|yaml|yml|properties|sql|json|gradle|bash|)?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE
    );

    private final GitHubConfig gitHubConfig;
    private final ObjectMapper objectMapper;

    public String pushCodeToGitHub(String generatedCode, String repoName, String executionId) throws Exception {
        log.info("Pushing generated Spring Batch code to GitHub. Repo: {}, Execution: {}", repoName, executionId);

        String finalRepoName = String.format("%s-%s-%s",
                repoName,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                UUID.randomUUID().toString().substring(0, 8)
        );

        String repoUrl = createRepository(finalRepoName);

        Map<String, String> files = parseGeneratedCode(generatedCode);
        log.info("Parsed {} files from generated code", files.size());

        if (files.isEmpty()) {
            log.warn("No structured files found — pushing full response as generated-code.md");
            pushFile(finalRepoName, "generated-code.md", generatedCode, "Add generated Spring Batch code");
        } else {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                pushFile(finalRepoName, entry.getKey(), entry.getValue(), "Add " + entry.getKey());
                log.debug("Pushed: {}", entry.getKey());
            }
        }

        log.info("Code pushed successfully to: {}", repoUrl);
        return repoUrl;
    }

    // Parse the LLM markdown response and extract individual files.
    // Strategy: for each code block, look back up to 400 chars for the nearest file path mention.
    private Map<String, String> parseGeneratedCode(String generatedCode) {
        Map<String, String> files = new LinkedHashMap<>();

        Matcher blockMatcher = CODE_BLOCK_PATTERN.matcher(generatedCode);
        while (blockMatcher.find()) {
            String codeContent = blockMatcher.group(1);
            if (codeContent == null || codeContent.isBlank()) continue;

            int lookbackStart = Math.max(0, blockMatcher.start() - 400);
            String preceding = generatedCode.substring(lookbackStart, blockMatcher.start());

            // Find the last file path mention in the preceding text
            String filePath = null;
            Matcher pathMatcher = FILE_PATH_PATTERN.matcher(preceding);
            while (pathMatcher.find()) {
                filePath = pathMatcher.group();
            }

            if (filePath != null && !files.containsKey(filePath)) {
                files.put(filePath, codeContent);
            }
        }

        return files;
    }

    private String createRepository(String repoName) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", repoName,
                "description", "Spring Batch code generated from Informatica IDMC export",
                "private", false,
                "auto_init", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API + "/user/repos"))
                .header("Authorization", "Bearer " + gitHubConfig.getApiToken())
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new RuntimeException("Failed to create GitHub repo: HTTP " + response.statusCode() + " - " + response.body());
        }

        String htmlUrl = (String) objectMapper.readValue(response.body(), Map.class).get("html_url");
        log.info("GitHub repository created: {}", htmlUrl);
        return htmlUrl;
    }

    private void pushFile(String repoName, String filePath, String content, String commitMessage) throws Exception {
        String encodedContent = Base64.getEncoder()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));

        String body = objectMapper.writeValueAsString(Map.of(
                "message", commitMessage,
                "content", encodedContent
        ));

        String owner = gitHubConfig.getRepoOwner();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API + "/repos/" + owner + "/" + repoName + "/contents/" + filePath))
                .header("Authorization", "Bearer " + gitHubConfig.getApiToken())
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            log.warn("Failed to push {}: HTTP {} - {}", filePath, response.statusCode(), response.body());
        }
    }
}
