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

    public String pushCodeToGitHub(String generatedCode, String repoName, String executionId) throws Exception {
        log.info("Pushing generated Spring Batch code to GitHub. Repo: {}, Execution: {}", repoName, executionId);

        String finalRepoName = String.format("%s-%s-%s",
                repoName,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                UUID.randomUUID().toString().substring(0, 8)
        );

        String repoUrl = createRepository(finalRepoName);
        pushReadme(finalRepoName, generatedCode, executionId);

        log.info("Code pushed successfully to: {}", repoUrl);
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

    private void pushReadme(String repoName, String generatedCode, String executionId) throws Exception {
        String readmeContent = String.format("""
                # Spring Batch Project

                Generated from Informatica IDMC export

                **Execution ID:** %s

                **Generated:** %s

                ## Project Structure

                This is a Spring Batch 5.2.3 project for Spring Boot 3.4.0 with Java 21.

                ## Build Instructions

                ```bash
                mvn clean install
                ```

                ## Generated Code

                ```
                %s
                ```

                ## Configuration

                Configure your database and scheduling parameters in `application-local.yml` or `application-prod.yml`.
                """,
                executionId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                generatedCode.substring(0, Math.min(1000, generatedCode.length())) + "..."
        );

        String encodedContent = Base64.getEncoder().encodeToString(readmeContent.getBytes());

        String body = objectMapper.writeValueAsString(Map.of(
                "message", "Initial commit: Generated Spring Batch project",
                "content", encodedContent
        ));

        String owner = gitHubConfig.getRepoOwner();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API + "/repos/" + owner + "/" + repoName + "/contents/README.md"))
                .header("Authorization", "Bearer " + gitHubConfig.getApiToken())
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new RuntimeException("Failed to push README: HTTP " + response.statusCode() + " - " + response.body());
        }

        log.debug("README pushed to GitHub repository: {}", repoName);
    }
}
