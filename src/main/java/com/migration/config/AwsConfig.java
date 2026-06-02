package com.migration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;
import lombok.Data;

@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client(AwsProperties awsProperties) {
        if (awsProperties.getS3().getAccessKey() != null && 
            !awsProperties.getS3().getAccessKey().equals("dummy-access-key")) {
            return S3Client.builder()
                    .region(Region.of(awsProperties.getRegion()))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(
                                            awsProperties.getS3().getAccessKey(),
                                            awsProperties.getS3().getSecretKey()
                                    )
                            )
                    )
                    .build();
        }
        return S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .build();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(AwsProperties awsProperties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(awsProperties.getBedrock().getRegion()))
                .build();
    }

    @Configuration
    @ConfigurationProperties(prefix = "aws")
    @Data
    public static class AwsProperties {
        private String region;
        private S3Properties s3;
        private BedrockProperties bedrock;

        @Data
        public static class S3Properties {
            private String bucketName;
            private String brsPath;
            private String accessKey;
            private String secretKey;
        }

        @Data
        public static class BedrockProperties {
            private String region;
            private String modelId;
        }
    }
}
