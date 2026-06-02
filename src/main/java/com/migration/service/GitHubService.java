package com.migration.service;

import com.migration.config.GitHubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private final GitHubConfig gitHubConfig;
    private final FileProcessingService fileProcessingService;

    public String pushCodeToGitHub(String generatedCode, String repoName, String executionId) throws Exception {
        log.info("Pushing generated Spring Batch code to GitHub. Repo: {}, Execution: {}", repoName, executionId);
        
        try {
            // Connect to GitHub
            GitHub github = GitHub.connectUsingOAuth(gitHubConfig.getApiToken());
            
            // Create repository name with timestamp
            String finalRepoName = String.format("%s-%s-%s", 
                    repoName, 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                    UUID.randomUUID().toString().substring(0, 8)
            );
            
            // Create repository
            GHRepository repository = github.createRepository(finalRepoName)
                    .description("Spring Batch 5.2.3 code generated from Informatica IDMC export")
                    .private_(false)
                    .create();
            
            log.info("GitHub repository created: {}", repository.getHtmlUrl());
            
            // Parse and push generated code files
            pushCodeFiles(repository, generatedCode, executionId);
            
            // Push initial commit
            String repoUrl = repository.getHtmlUrl().toString();
            log.info("Code pushed successfully to: {}", repoUrl);
            
            return repoUrl;
            
        } catch (IOException e) {
            log.error("Error pushing code to GitHub: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void pushCodeFiles(GHRepository repository, String generatedCode, String executionId) throws Exception {
        log.debug("Processing and pushing generated code files to repository");
        
        // Create temp directory for code generation
        Path tempDir = fileProcessingService.createTempDirectory("github-" + executionId);
        
        try {
            // Parse the generated code and create file structure
            // This would contain logic to create pom.xml, Java files, etc.
            
            // For now, create a README with the generated code
            String readmeContent = createReadmeContent(generatedCode, executionId);
            
            // Push README file
            repository.createContent()
                    .path("README.md")
                    .message("Initial commit: Generated Spring Batch project")
                    .content(readmeContent)
                    .commit();
            
            log.debug("Code files pushed to GitHub repository");
            
        } finally {
            // Cleanup
            cleanupDirectory(tempDir);
        }
    }

    private String createReadmeContent(String generatedCode, String executionId) {
        return String.format("""
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
                """, executionId, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                generatedCode.substring(0, Math.min(1000, generatedCode.length())) + "..."
        );
    }

    private void cleanupDirectory(Path directory) {
        try {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Could not delete: {}", path);
                        }
                    });
            log.debug("Cleaned up directory: {}", directory);
        } catch (IOException e) {
            log.warn("Error cleaning up directory {}: {}", directory, e.getMessage());
        }
    }
}
