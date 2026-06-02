# Migration Service - Informatica to Spring Batch Code Generator

## Overview

This Spring Boot 4 service provides two REST API endpoints for migrating Informatica IDMC exports to Spring Batch projects:

1. **BRS Generation Endpoint** - Uploads a ZIP file containing Informatica IDMC export JSON files and generates a Business Requirements Document (BRD) via AWS Bedrock
2. **Spring Batch Code Generation Endpoint** - Consumes the BRD and generates a complete Spring Batch 5.2.3 project for Spring Boot 3.4.0, pushed to GitHub

## Technical Stack

- **Java Version:** 21
- **Spring Boot:** 4.0.0
- **Spring Batch:** 5.2.3 (generated in target projects)
- **Build Tool:** Maven 3.9+
- **AWS SDK:** 2.28.1
- **Cloud Integration:** AWS Bedrock, AWS S3, GitHub API

## Prerequisites

1. Java 21 installed and configured
2. Maven 3.9 or later
3. AWS Account with:
   - Bedrock access (Claude model)
   - S3 bucket for BRS file storage
   - IAM credentials configured locally or in environment
4. GitHub Personal Access Token with repository creation permissions

## Configuration

### Local Environment (`application-local.yml`)

```yaml
aws:
  region: us-east-1
  s3:
    bucket-name: migration-brs-bucket-local
    brs-path: brs/
    access-key: dummy-access-key  # Replace with actual AWS access key
    secret-key: dummy-secret-key  # Replace with actual AWS secret key
  bedrock:
    region: us-east-1
    model-id: claude-3-5-sonnet-20241022

github:
  api-token: dummy-github-token  # Replace with actual GitHub Personal Access Token
  repo-owner: your-username      # Replace with your GitHub username
  repo-base-url: https://github.com
```

### Production Environment (`application-prod.yml`)

Use environment variables for sensitive configuration:

```bash
export AWS_REGION=us-east-1
export AWS_S3_BUCKET=migration-brs-bucket-prod
export AWS_ACCESS_KEY=your-aws-access-key
export AWS_SECRET_KEY=your-aws-secret-key
export AWS_BEDROCK_REGION=us-east-1
export AWS_BEDROCK_MODEL=claude-3-5-sonnet-20241022
export GITHUB_TOKEN=your-github-token
export GITHUB_REPO_OWNER=your-username
```

## Building the Service

### Clean Build

```bash
mvn clean install
```

### Build with Tests and Coverage Report

```bash
mvn clean test jacoco:report install
```

Coverage reports will be generated in: `target/site/jacoco/index.html`

### Build Summary

The Maven build will:
1. Compile Java source code with Java 21
2. Run unit tests
3. Generate JaCoCo code coverage report
4. Create executable JAR with embedded Tomcat
5. Package all dependencies

## Running the Service

### Local Development

```bash
# Using Maven
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Using Java directly
java -jar target/migration-service-1.0.0.jar --spring.profiles.active=local
```

### Production Deployment

```bash
# With environment variables
java -jar migration-service-1.0.0.jar \
  --spring.profiles.active=prod \
  --aws.region=$AWS_REGION \
  --aws.s3.bucket-name=$AWS_S3_BUCKET \
  --aws.s3.access-key=$AWS_ACCESS_KEY \
  --aws.s3.secret-key=$AWS_SECRET_KEY \
  --github.api-token=$GITHUB_TOKEN \
  --github.repo-owner=$GITHUB_REPO_OWNER
```

## API Endpoints

### 1. Generate BRS from Informatica Export

**Endpoint:** `POST /api/migrate/generate-brs`

**Request:**
- Content-Type: multipart/form-data
- Parameter: `file` (ZIP file containing Informatica IDMC export JSON files)

**Example using cURL:**

```bash
curl -X POST http://localhost:8080/api/migrate/generate-brs \
  -F "file=@informatica-idmc-export.zip"
```

**Example using Python:**

```python
import requests

with open('informatica-idmc-export.zip', 'rb') as f:
    files = {'file': f}
    response = requests.post('http://localhost:8080/api/migrate/generate-brs', files=files)
    print(response.json())
```

**Response:**

```json
{
  "status": "SUCCESS",
  "message": "BRS generated successfully",
  "brs_file_path": "s3://migration-brs-bucket-local/brs/brs-<execution-id>.md",
  "execution_id": "<uuid>",
  "timestamp": "2024-06-02T10:30:45"
}
```

**Error Response (400):**

```json
{
  "status": "FAILED",
  "message": "Invalid input",
  "error_details": "Only ZIP files are supported",
  "execution_id": "<uuid>",
  "timestamp": "2024-06-02T10:30:45"
}
```

### 2. Generate Spring Batch Code from BRS

**Endpoint:** `POST /api/migrate/generate-code`

**Request Parameters:**
- `brs_s3_path` (string): S3 path to BRS file (from Step 1 response)
- `repo_name` (string): Desired repository name prefix

**Example using cURL:**

```bash
curl -X POST http://localhost:8080/api/migrate/generate-code \
  -G \
  --data-urlencode "brs_s3_path=s3://migration-brs-bucket-local/brs/brs-<execution-id>.md" \
  --data-urlencode "repo_name=spring-batch-migration"
```

**Example using Python:**

```python
import requests

params = {
    'brs_s3_path': 's3://migration-brs-bucket-local/brs/brs-<execution-id>.md',
    'repo_name': 'spring-batch-migration'
}
response = requests.post('http://localhost:8080/api/migrate/generate-code', params=params)
print(response.json())
```

**Response:**

```json
{
  "status": "SUCCESS",
  "message": "Spring Batch code generated and pushed to GitHub",
  "spring_batch_repo_url": "https://github.com/username/spring-batch-migration-20240602130000",
  "execution_id": "<uuid>",
  "timestamp": "2024-06-02T13:00:45"
}
```

## Testing

### Running Unit Tests

```bash
mvn test
```

### Running Specific Test Class

```bash
mvn test -Dtest=MigrationControllerTest
```

### Running with Coverage

```bash
mvn clean test jacoco:report
```

### Test Coverage Report

Open `target/site/jacoco/index.html` in a browser to view coverage metrics.

## Sample Informatica IDMC Export

A sample ZIP file with Informatica IDMC export JSON files is included in `sample-inputs/informatica-idmc-export.zip`.

**Contents:**
- `mappings.json` - Mapping definitions with source/target configurations
- `workflows.json` - Workflow scheduling and task definitions
- `connections.json` - Connection and data asset definitions

Use this for testing the `/api/migrate/generate-brs` endpoint.

## Project Structure

```
migration-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/migration/
│   │   │   ├── MigrationServiceApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AwsConfig.java
│   │   │   │   ├── GitHubConfig.java
│   │   │   │   └── CommonConfig.java
│   │   │   ├── controller/
│   │   │   │   └── MigrationController.java
│   │   │   ├── service/
│   │   │   │   ├── BrsGenerationService.java
│   │   │   │   ├── SpringBatchCodeGenerationService.java
│   │   │   │   ├── GitHubService.java
│   │   │   │   └── FileProcessingService.java
│   │   │   └── dto/
│   │   │       └── MigrationResponse.java
│   │   └── resources/
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       └── prompts/
│   │           ├── brd-prompt.txt
│   │           └── code-generation-prompt.txt
│   ├── test/
│   │   └── java/com/migration/
│   │       └── controller/
│   │           └── MigrationControllerTest.java
├── sample-inputs/
│   ├── mappings.json
│   ├── workflows.json
│   ├── connections.json
│   └── informatica-idmc-export.zip
└── README.md
```

## Build Summary

When running `mvn clean install`:

1. **Compilation Phase**
   - Java 21 compilation
   - Unused imports cleaned
   - Type checking and validation

2. **Testing Phase**
   - Unit tests executed
   - JaCoCo code coverage collected
   - Coverage reports generated

3. **Packaging Phase**
   - Spring Boot executable JAR created
   - All dependencies embedded
   - Application properties packaged

4. **Final Artifacts**
   - Target JAR: `migration-service-1.0.0.jar`
   - Dependency copy: `target/dependency/`
   - Coverage Report: `target/site/jacoco/index.html`

## Security Considerations

1. **Credential Management**
   - Never commit credentials to version control
   - Use environment variables for production
   - Rotate AWS access keys and GitHub tokens regularly

2. **S3 Bucket Configuration**
   - Enable versioning for BRS files
   - Set up bucket encryption (SSE-S3 or SSE-KMS)
   - Restrict public access

3. **GitHub Repository**
   - Generated repositories are public by default
   - Consider making them private if handling sensitive data
   - Review MCP server permissions before use

## Troubleshooting

### AWS Bedrock Connection Error

```
Error: Unable to connect to Bedrock service
```

**Solutions:**
1. Verify AWS credentials are correctly configured
2. Check AWS region supports Claude model
3. Ensure IAM user has `bedrock:InvokeModel` permission

### S3 Upload Failure

```
Error: Access Denied to S3 bucket
```

**Solutions:**
1. Verify bucket exists and is accessible
2. Check IAM credentials have S3 PutObject permission
3. Verify bucket policy allows the configured credentials

### GitHub Repository Creation Failed

```
Error: Cannot create repository
```

**Solutions:**
1. Verify GitHub token is valid and has repo scope
2. Check token hasn't expired
3. Ensure GitHub account doesn't have repo creation limits

## Performance Optimization

1. **Chunk Size for Code Generation:** Set to 1000 for initial load
2. **Batch Commit Interval:** 1000 records for optimal throughput
3. **Database Connection Pooling:** Configured via Spring DataSource
4. **Async Processing:** Consider enabling for large file processing

## Monitoring and Logging

Application logs are written to:
- **Console:** Real-time logs during development
- **File:** `logs/migration-service.log` (local) or `/var/log/migration-service/application.log` (prod)

Log levels by profile:
- **Local:** DEBUG
- **Production:** INFO for application, WARN for framework

## Support and Contributions

For issues, enhancements, or contributions:
1. Review existing GitHub issues
2. Create detailed bug reports with reproduction steps
3. Follow the existing code style and conventions
4. Ensure all tests pass before submitting PRs

## License

This project is part of the Informatica to Spring Batch migration toolkit.
