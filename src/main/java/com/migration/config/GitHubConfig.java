package com.migration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "github")
@Data
public class GitHubConfig {
    private String apiToken;
    private String repoOwner;
    private String repoBaseUrl;
}
