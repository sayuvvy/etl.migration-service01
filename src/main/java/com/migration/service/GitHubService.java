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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private static final String GITHUB_API = "https://api.github.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitHubConfig gitHubConfig;
    private final ObjectMapper objectMapper;

    public String pushFilesToGitHub(Map<String, String> files, String repoName, String executionId) throws Exception {
        log.info("Pushing {} files to GitHub. Repo: {}, Execution: {}", files.size(), repoName, executionId);

        String finalRepoName = String.format("%s-%s-%s",
                repoName,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                UUID.randomUUID().toString().substring(0, 8)
        );

        String repoUrl = createRepository(finalRepoName);

        if (files.isEmpty()) {
            log.warn("No files to push — repository created but empty");
        } else {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                pushFile(finalRepoName, entry.getKey(), entry.getValue(), "Add " + entry.getKey());
                log.debug("Pushed: {}", entry.getKey());
            }
        }

        log.info("All files pushed to: {}", repoUrl);
        return repoUrl;
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
