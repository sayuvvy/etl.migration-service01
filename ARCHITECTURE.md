# Architecture & AWS Bedrock MCP Integration Guide

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   Migration Service (Spring Boot 4)              │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              REST API Layer                              │   │
│  │  POST /api/migrate/generate-brs                         │   │
│  │  POST /api/migrate/generate-code                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │          Service Layer                                   │   │
│  │  ├─ BrsGenerationService                               │   │
│  │  ├─ SpringBatchCodeGenerationService                   │   │
│  │  ├─ FileProcessingService                              │   │
│  │  └─ GitHubService                                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │          Integration Layer                              │   │
│  │  ├─ AWS Bedrock (Claude 3.5 Sonnet)                    │   │
│  │  ├─ AWS S3 (BRS Storage)                               │   │
│  │  └─ GitHub API (Repository Management)                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
         ↓                    ↓                    ↓
    ┌────────┐           ┌────────┐           ┌────────┐
    │  AWS   │           │  AWS   │           │ GitHub │
    │ Bedrock│           │  S3    │           │  Repos │
    └────────┘           └────────┘           └────────┘
```

## Data Flow

### Stage 1: BRS Generation

```
Input: Informatica IDMC Export ZIP
    ↓
[1.1] File Upload Handler (MultipartFile)
    ↓
[1.2] ZIP Extract & Parse JSON Files
    ├─ mappings.json
    ├─ workflows.json
    └─ connections.json
    ↓
[1.3] AWS Bedrock Invocation
    ├─ System Prompt: BRD Generation Rules
    └─ User Prompt: Extracted Informatica Metadata
    ↓
[1.4] Claude 3.5 Sonnet (LLM Processing)
    ├─ Analyze Informatica structures
    ├─ Extract business rules
    ├─ Generate BRD markdown
    └─ Response: ~4000 tokens
    ↓
[1.5] S3 Upload
    └─ s3://bucket/brs/brs-{executionId}-{uuid}.md
    ↓
Output: BRS File Path (S3 URL)
```

### Stage 2: Spring Batch Code Generation

```
Input: BRS File Path (from Stage 1)
    ↓
[2.1] BRS Retrieval from S3
    └─ Read markdown file content
    ↓
[2.2] AWS Bedrock Invocation
    ├─ System Prompt: Spring Batch Code Generation Rules
    │  (from BRDToSpringBatch.txt)
    └─ User Prompt: BRS Content + Code Generation Requirements
    ↓
[2.3] Claude 3.5 Sonnet (Code Generation)
    ├─ Analyze BRS requirements
    ├─ Generate Spring Batch 5.2.3 project structure
    ├─ Create Java classes
    │  ├─ BatchApplication.java
    │  ├─ Config classes (Job, Step, DataSource, etc.)
    │  ├─ Model classes
    │  ├─ Reader/Processor/Writer implementations
    │  ├─ Listeners and Interceptors
    │  └─ Test classes
    ├─ Generate pom.xml with dependencies
    ├─ Generate application-{profile}.yml files
    └─ Response: ~8000 tokens
    ↓
[2.4] Code Parsing & File Organization
    └─ Extract file paths and content from LLM response
    ↓
[2.5] GitHub Repository Creation
    ├─ Create new repository
    │  ├─ Name: {repoName}-{timestamp}-{uuid}
    │  ├─ Description: Auto-generated from Informatica
    │  └─ Visibility: Public (configurable)
    ├─ Initialize repository
    └─ Push initial commit with code files
    ↓
[2.6] Repository Verification
    └─ Confirm all files committed successfully
    ↓
Output: GitHub Repository URL
```

## AWS Bedrock Integration

### Bedrock Model Configuration

```
Model: Claude 3.5 Sonnet
Provider: Anthropic
Region: us-east-1 (configurable)
Context Window: 200k tokens
Max Output Tokens: 8000 (configurable per request)
Temperature: 0.7 (default)
```

### API Call Structure

#### BRS Generation Request

```json
{
  "anthropic_version": "bedrock-2023-06-01",
  "max_tokens": 4000,
  "system": "<BRD generation prompt from resources>",
  "messages": [
    {
      "role": "user",
      "content": "Based on the following Informatica IDMC export, generate a comprehensive Business Requirements Document..."
    }
  ]
}
```

#### Code Generation Request

```json
{
  "anthropic_version": "bedrock-2023-06-01",
  "max_tokens": 8000,
  "system": "<Spring Batch code generation prompt from BRDToSpringBatch.txt>",
  "messages": [
    {
      "role": "user",
      "content": "Based on the following Business Requirements Document, generate a complete Spring Batch 5.2.3 project..."
    }
  ]
}
```

### Response Handling

```
Bedrock Response JSON:
├─ content[]
│  ├─ type: "text"
│  └─ text: "<Generated BRS or Code>"
├─ stop_reason: "end_turn"
├─ usage
│  ├─ input_tokens: 1234
│  └─ output_tokens: 5678
└─ model: "claude-3-5-sonnet-20241022"
```

## AWS S3 Integration

### Bucket Configuration

```
Bucket: migration-brs-bucket-{environment}
  ├─ brs/
  │  ├─ brs-{executionId}-{uuid}.md
  │  ├─ brs-{executionId}-{uuid}.md
  │  └─ ...
  ├─ temp/
  │  ├─ {tempFileId}.zip
  │  └─ ...
  └─ logs/
     ├─ migration-service.log
     └─ ...
```

### Security Configuration

```yaml
Versioning: Enabled
Encryption: SSE-S3 (default) or SSE-KMS
Access Control: Private
Public Access Block: All enabled
CORS: Restricted to service domain
Lifecycle Rules:
  - Move BRS to Glacier after 90 days
  - Delete temp files after 7 days
  - Retain logs for 365 days
```

## GitHub Integration

### Repository Structure (Generated)

```
spring-batch-migration-{timestamp}-{uuid}/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/migration/batch/
│   │   │   ├── BatchApplication.java
│   │   │   ├── config/
│   │   │   │   ├── JobConfig.java
│   │   │   │   ├── StepConfig.java
│   │   │   │   ├── DataSourceConfig.java
│   │   │   │   ├── SchedulerConfig.java
│   │   │   │   ├── InterceptorConfig.java
│   │   │   │   └── CommonBeanConfig.java
│   │   │   ├── model/
│   │   │   │   ├── SourceRecord.java
│   │   │   │   └── TargetRecord.java
│   │   │   ├── reader/
│   │   │   │   └── SourceItemReader.java
│   │   │   ├── processor/
│   │   │   │   ├── DataValidator.java
│   │   │   │   └── DataTransformer.java
│   │   │   ├── writer/
│   │   │   │   └── TargetItemWriter.java
│   │   │   ├── listener/
│   │   │   │   ├── JobLoggingListener.java
│   │   │   │   ├── StepLoggingListener.java
│   │   │   │   └── ChunkLoggingListener.java
│   │   │   ├── interceptor/
│   │   │   │   └── StepInterceptor.java
│   │   │   └── policy/
│   │   │       └── SkipRetryPolicy.java
│   │   └── resources/
│   │       ├── application-local.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/com/migration/batch/
│           └── BatchApplicationTest.java
├── .github/workflows/
│   └── maven-build.yml (optional)
└── target/
    └── generated-code-summary.md
```

### Authentication

```java
GitHub github = GitHub.connectUsingOAuth(apiToken);
// Permissions required:
// - repo (full control of private repositories)
// - workflow (update GitHub Action workflows)
// - admin:public_key (manage deploy keys)
```

## Configuration Profiles

### Local Profile (application-local.yml)

```yaml
# Development environment
spring:
  profiles:
    active: local

aws:
  s3:
    bucket-name: migration-brs-bucket-local
    access-key: [local AWS key]
    secret-key: [local AWS secret]

github:
  api-token: [local GitHub token]
```

### Production Profile (application-prod.yml)

```yaml
# Production environment with environment variables
spring:
  profiles:
    active: prod

aws:
  region: ${AWS_REGION}
  s3:
    bucket-name: ${AWS_S3_BUCKET}
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}

github:
  api-token: ${GITHUB_TOKEN}
  repo-owner: ${GITHUB_REPO_OWNER}
```

## Error Handling Strategy

### BRS Generation Errors

| Error | Cause | Recovery |
|-------|-------|----------|
| Invalid ZIP format | Uploaded file is not ZIP | Return 400 Bad Request |
| Bedrock API timeout | LLM processing delayed | Retry up to 3 times with backoff |
| S3 upload failure | AWS credentials invalid | Log error, return 500 |
| Out of memory | Large ZIP file | Increase JVM heap, stream processing |

### Code Generation Errors

| Error | Cause | Recovery |
|-------|-------|----------|
| BRS file not found | S3 path incorrect | Verify BRS generation completed |
| GitHub token invalid | Token expired/revoked | Return 401 Unauthorized |
| Repository creation failed | GitHub account limit | Retry with different repo name |
| Code syntax invalid | LLM generation error | Request code regeneration |

## Performance Optimization

### Request Optimization

```
BRS Generation:
├─ ZIP file size: Limit to 100MB
├─ Bedrock latency: ~10-30 seconds
└─ Total time: 45-90 seconds

Code Generation:
├─ BRS file size: Typical 50-100KB
├─ Bedrock latency: ~20-45 seconds
├─ GitHub push: ~5-10 seconds
└─ Total time: 60-120 seconds
```

### Connection Pooling

```java
// AWS SDK handles connection pooling automatically
// S3Client and BedrockRuntimeClient configured with:
// - Max concurrent requests: 50
// - Connection timeout: 30 seconds
// - Socket timeout: 60 seconds
// - Retry strategy: Exponential backoff
```

### Caching Strategy

```
BRS Cache: No caching (always generate fresh)
Code Generation: No caching (unique per request)
GitHub Credentials: Cached for session duration
File Upload: Temp storage (cleaned up after processing)
```

## Monitoring and Observability

### Application Metrics

```
/actuator/metrics/
├─ http.server.requests
├─ process.cpu.usage
├─ jvm.memory.used
├─ system.load.average
└─ custom metrics
```

### Logging Strategy

```
Local: Console + File (logs/migration-service.log)
Prod: File only (/var/log/migration-service/application.log)
Format: [timestamp] [thread] [level] [logger] message
Levels:
  - ERROR: Service failures
  - WARN: Degraded performance
  - INFO: API calls, file uploads
  - DEBUG: Variable inspection (local only)
```

### Health Checks

```
/actuator/health
├─ status: "UP"
├─ components
│  ├─ bedrock: Connected
│  ├─ s3: Connected
│  └─ github: Connected
└─ checks...
```

## Compliance & Security

### Data Privacy

- BRS files stored in encrypted S3
- ZIP uploads not retained (cleaned after processing)
- GitHub repositories public by default (configurable)
- No data transmitted outside AWS/GitHub

### Audit Trail

```
Logged Events:
├─ File uploads (filename, size, timestamp)
├─ BRS generation (executionId, status, S3 path)
├─ Code generation (executionId, repo URL, timestamp)
├─ Bedrock API calls (tokens used, response time)
└─ GitHub operations (repo created, commit hash)
```

### Compliance Checklist

- ✅ HTTPS only (TLS 1.2+)
- ✅ Credentials not in logs
- ✅ No hardcoded secrets
- ✅ IAM policy least privilege
- ✅ S3 encryption enabled
- ✅ Audit logging enabled

## Disaster Recovery

### Backup Strategy

```
Critical Data:
├─ BRS files: Daily backup to secondary bucket
├─ GitHub repos: Public repositories (no backup needed)
└─ Application logs: Retain for 30 days
```

### Failure Scenarios

| Scenario | Impact | Recovery Time |
|----------|--------|----------------|
| AWS Bedrock down | BRS generation blocked | 30 min (manual retry) |
| S3 unavailable | Cannot store BRS | 15 min (service restart) |
| GitHub API down | Cannot create repos | 10 min (manual creation) |
| Network latency | Slow processing | None (graceful handling) |

## Scaling Considerations

### Horizontal Scaling

```
Load Balancer (ALB/NLB)
    ↓
    ├─ Migration Service Instance 1
    ├─ Migration Service Instance 2
    ├─ Migration Service Instance 3
    └─ Migration Service Instance N

Shared Resources:
├─ AWS S3 Bucket (regional)
├─ GitHub Organization (shared token)
└─ RDS/Database (optional, for state)
```

### Vertical Scaling

```
Current (Single Instance):
├─ Memory: 2GB
├─ CPU: 2 cores
└─ Throughput: ~10 concurrent requests

Scaled (Production):
├─ Memory: 4-8GB
├─ CPU: 4-8 cores
└─ Throughput: ~100 concurrent requests
```

## Cost Optimization

### AWS Bedrock Pricing

```
Per 1M input tokens: $3.00 (Claude 3.5 Sonnet)
Per 1M output tokens: $15.00 (Claude 3.5 Sonnet)

Typical Usage:
├─ BRS Generation: ~2000 input + 3000 output tokens
├─ Code Generation: ~3000 input + 6000 output tokens
├─ Cost per migration: ~$0.15-0.20
├─ 100 migrations/month: ~$15-20
└─ Cost is minimal vs traditional ETL development
```

### AWS S3 Pricing

```
Storage: $0.023 per GB/month
Requests: $0.0004 per 1000 PUT requests
Transfer: Free within region
Data Retention: 90 days (cost: ~$2/year for 1000 BRS files)
```

### GitHub Pricing

```
Free: Public repositories
Pro: $4/month (if using private repos)
Organization: Custom pricing
API Calls: Unlimited (rate limited)
Storage: Unlimited for public repos
```
