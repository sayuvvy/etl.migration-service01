# Quick Start Guide

## 5-Minute Setup

### Step 1: Prerequisites
```bash
java -version  # Ensure Java 21
mvn -version   # Ensure Maven 3.9+
```

### Step 2: Configure AWS Credentials
```bash
# Option A: AWS CLI configuration
aws configure

# Option B: Environment variables
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_DEFAULT_REGION=us-east-1
```

### Step 3: Get GitHub Token
1. Go to https://github.com/settings/tokens
2. Create "Personal access token (classic)"
3. Select scopes: `repo`, `workflow`
4. Save token securely

### Step 4: Update Configuration
Edit `application-local.yml`:
```yaml
aws:
  region: us-east-1
  s3:
    bucket-name: your-s3-bucket-name
    access-key: your-aws-key
    secret-key: your-aws-secret

github:
  api-token: your-github-token
  repo-owner: your-github-username
```

### Step 5: Run the Service
```bash
cd migration-service
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Expected output:
```
2024-06-02 10:30:45 - Tomcat started on port 8080
2024-06-02 10:30:45 - MigrationServiceApplication started in 8.234s
```

## Usage Examples

### Example 1: Generate BRS from Sample Informatica Export

```bash
curl -X POST http://localhost:8080/api/migrate/generate-brs \
  -F "file=@sample-inputs/informatica-idmc-export.zip"
```

**Response:**
```json
{
  "status": "SUCCESS",
  "message": "BRS generated successfully",
  "brs_file_path": "s3://migration-brs-bucket-local/brs/brs-a1b2c3d4-xyz.md",
  "execution_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2024-06-02T10:30:45"
}
```

### Example 2: Generate Spring Batch Code

Using the BRS path from Example 1:

```bash
curl -X POST http://localhost:8080/api/migrate/generate-code \
  --data-urlencode "brs_s3_path=s3://migration-brs-bucket-local/brs/brs-a1b2c3d4-xyz.md" \
  --data-urlencode "repo_name=my-spring-batch-project"
```

**Response:**
```json
{
  "status": "SUCCESS",
  "message": "Spring Batch code generated and pushed to GitHub",
  "spring_batch_repo_url": "https://github.com/yourusername/my-spring-batch-project-20240602103045-abc12345",
  "execution_id": "b2c3d4e5-f6f7-8901-bcde-f12345678901",
  "timestamp": "2024-06-02T10:30:55"
}
```

### Example 3: Using Python Client

```python
import requests
import json

# Step 1: Generate BRS
with open('informatica-idmc-export.zip', 'rb') as f:
    files = {'file': f}
    response = requests.post('http://localhost:8080/api/migrate/generate-brs', files=files)
    result = response.json()

if result['status'] == 'SUCCESS':
    brs_path = result['brs_file_path']
    
    # Step 2: Generate Code
    params = {
        'brs_s3_path': brs_path,
        'repo_name': 'my-spring-batch-app'
    }
    code_response = requests.post('http://localhost:8080/api/migrate/generate-code', params=params)
    code_result = code_response.json()
    
    if code_result['status'] == 'SUCCESS':
        print(f"Repo created: {code_result['spring_batch_repo_url']}")
```

### Example 4: Using Node.js Client

```javascript
const axios = require('axios');
const FormData = require('form-data');
const fs = require('fs');

async function migrateInfomatikaToSpringBatch() {
  // Step 1: Generate BRS
  const form = new FormData();
  form.append('file', fs.createReadStream('informatica-idmc-export.zip'));
  
  const brsResponse = await axios.post(
    'http://localhost:8080/api/migrate/generate-brs',
    form,
    { headers: form.getHeaders() }
  );
  
  const brsPath = brsResponse.data.brs_file_path;
  console.log('BRS generated:', brsPath);
  
  // Step 2: Generate Code
  const codeResponse = await axios.post(
    'http://localhost:8080/api/migrate/generate-code',
    null,
    {
      params: {
        brs_s3_path: brsPath,
        repo_name: 'my-spring-batch-app'
      }
    }
  );
  
  console.log('Code generated:', codeResponse.data.spring_batch_repo_url);
}

migrateInfomatikaToSpringBatch().catch(console.error);
```

## Troubleshooting

### Issue: Service won't start
```
Error: Failed to connect to Bedrock
```
**Solution:**
```bash
# Check AWS credentials
aws sts get-caller-identity

# Verify Bedrock access
aws bedrock list-foundation-models --region us-east-1
```

### Issue: File upload fails
```
Error: Only ZIP files are supported
```
**Solution:**
```bash
# Verify file is ZIP format
file informatica-idmc-export.zip

# Should output: Zip archive data
```

### Issue: S3 upload fails
```
Error: Access Denied to S3 bucket
```
**Solution:**
```bash
# Check S3 bucket exists
aws s3 ls s3://migration-brs-bucket-local

# Check IAM permissions
aws iam get-user-policy --user-name your-user --policy-name-your-policy
```

### Issue: GitHub authentication fails
```
Error: Cannot authenticate with GitHub
```
**Solution:**
```bash
# Verify token is valid
curl -H "Authorization: token YOUR_TOKEN" https://api.github.com/user

# Check token has required scopes
# Visit: https://github.com/settings/tokens/YOUR_TOKEN_ID
```

## Common Tasks

### Task 1: Process Multiple Informatica Exports

```bash
#!/bin/bash

# Process multiple ZIP files
for zip_file in exports/*.zip; do
  echo "Processing $zip_file..."
  
  # Generate BRS
  response=$(curl -s -X POST http://localhost:8080/api/migrate/generate-brs \
    -F "file=@$zip_file")
  
  brs_path=$(echo $response | jq -r '.brs_file_path')
  repo_name=$(basename $zip_file .zip)
  
  # Generate Code
  curl -s -X POST http://localhost:8080/api/migrate/generate-code \
    --data-urlencode "brs_s3_path=$brs_path" \
    --data-urlencode "repo_name=$repo_name" | jq '.'
done
```

### Task 2: Download Generated BRS Files

```bash
#!/bin/bash

# List and download all BRS files from S3
aws s3 ls s3://migration-brs-bucket-local/brs/ | awk '{print $4}' | while read file; do
  aws s3 cp "s3://migration-brs-bucket-local/brs/$file" "./downloaded-brs/$file"
done
```

### Task 3: Monitor API Performance

```bash
#!/bin/bash

# Simple performance monitoring
for i in {1..10}; do
  echo "Request $i..."
  time curl -X POST http://localhost:8080/api/migrate/generate-brs \
    -F "file=@sample-inputs/informatica-idmc-export.zip" > /dev/null 2>&1
  sleep 2
done
```

### Task 4: Generate Documentation from Generated Code

```bash
#!/bin/bash

# Clone generated repository
repo_url="$1"
repo_name=$(echo $repo_url | grep -oE '[^/]+\.git$' | sed 's/.git$//')

git clone $repo_url
cd $repo_name

# Generate documentation
mvn clean javadoc:javadoc
mvn site
```

## Performance Benchmarks

### Expected Response Times

```
BRS Generation:
├─ File upload: 2-5 seconds
├─ Informatica parsing: 3-5 seconds
├─ Bedrock API call: 15-30 seconds
├─ S3 upload: 2-3 seconds
└─ Total: 45-90 seconds

Code Generation:
├─ BRS retrieval: 2-3 seconds
├─ Bedrock API call: 20-45 seconds
├─ GitHub repo creation: 3-5 seconds
├─ Code push: 2-4 seconds
└─ Total: 60-120 seconds

Total Pipeline: 120-210 seconds (2-3.5 minutes)
```

## Local Development Setup

### Enable Debug Logging

Edit `application-local.yml`:
```yaml
logging:
  level:
    com.migration: DEBUG
    software.amazon.awssdk: DEBUG
```

### Run with IDE

**IntelliJ IDEA:**
1. File → Open → Select migration-service directory
2. Run → Edit Configurations
3. Create new Spring Boot configuration
4. Main class: `com.migration.MigrationServiceApplication`
5. VM options: `-Dspring.profiles.active=local`
6. Run with Shift+F10

**VS Code:**
1. Install Extension Pack for Java
2. Open integrated terminal
3. Run: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"`

### Testing Locally

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=MigrationControllerTest

# Run with coverage
mvn test jacoco:report
open target/site/jacoco/index.html
```

## Next Steps

1. **Customize Prompts**
   - Edit `src/main/resources/prompts/brd-prompt.txt`
   - Edit `src/main/resources/prompts/code-generation-prompt.txt`

2. **Extend Code Generation**
   - Modify `SpringBatchCodeGenerationService`
   - Add support for additional Informatica components

3. **Add Database Support**
   - Implement `DataSourceConfig` for your database
   - Update job configuration to use custom datasources

4. **Scale to Production**
   - Follow BUILD_AND_DEPLOYMENT.md
   - Deploy to Docker/Kubernetes
   - Configure AWS CloudWatch monitoring

## Support & Documentation

- **Full API Documentation:** See README.md
- **Architecture Details:** See ARCHITECTURE.md
- **Build & Deployment:** See BUILD_AND_DEPLOYMENT.md
- **Generated Project Sample:** sample-inputs/informatica-idmc-export.zip

## Getting Help

1. Check logs: `tail -f logs/migration-service.log`
2. Review ARCHITECTURE.md for design patterns
3. Consult BUILD_AND_DEPLOYMENT.md for deployment issues
4. Check AWS Bedrock documentation: https://docs.aws.amazon.com/bedrock/
5. Review Spring Boot guides: https://spring.io/guides

---

**Estimated time to first successful migration: 10-15 minutes**
