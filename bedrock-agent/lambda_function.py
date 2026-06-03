import json
import boto3
import urllib.request
import urllib.parse
import uuid

EB_BASE_URL = "http://migration-service-env.eba-p5zrdeku.eu-north-1.elasticbeanstalk.com/api/migrate"


def lambda_handler(event, context):
    print(f"Event received: {json.dumps(event)}")

    action_group = event.get("actionGroup", "")
    api_path = event.get("apiPath", "")
    http_method = event.get("httpMethod", "POST")
    request_body = event.get("requestBody", {})

    # Parse properties from Bedrock Agent request body format
    body = {}
    if request_body:
        content = request_body.get("content", {})
        if "application/json" in content:
            for prop in content["application/json"].get("properties", []):
                body[prop["name"]] = prop["value"]

    print(f"Path: {api_path}, Body: {body}")

    try:
        if api_path == "/generate-brs":
            result = handle_generate_brs(body)
            status_code = 200
        elif api_path == "/generate-code":
            result = handle_generate_code(body)
            status_code = 200
        else:
            result = {"status": "FAILED", "message": f"Unknown action path: {api_path}"}
            status_code = 400
    except Exception as e:
        print(f"Error: {str(e)}")
        result = {"status": "FAILED", "message": str(e)}
        status_code = 500

    return {
        "messageVersion": "1.0",
        "response": {
            "actionGroup": action_group,
            "apiPath": api_path,
            "httpMethod": http_method,
            "httpStatusCode": status_code,
            "responseBody": {
                "application/json": {
                    "body": json.dumps(result)
                }
            }
        }
    }


def handle_generate_brs(body):
    s3_bucket = body.get("s3_bucket")
    s3_key = body.get("s3_key")

    if not s3_bucket or not s3_key:
        raise ValueError("s3_bucket and s3_key are required")

    print(f"Downloading s3://{s3_bucket}/{s3_key}")
    s3_client = boto3.client("s3")
    file_obj = s3_client.get_object(Bucket=s3_bucket, Key=s3_key)
    file_content = file_obj["Body"].read()
    file_name = s3_key.split("/")[-1]

    # Build multipart form-data manually (no external libraries needed)
    boundary = uuid.uuid4().hex
    crlf = b"\r\n"
    body_parts = (
        f"--{boundary}".encode() + crlf
        + f'Content-Disposition: form-data; name="file"; filename="{file_name}"'.encode() + crlf
        + b"Content-Type: application/octet-stream" + crlf
        + crlf
        + file_content + crlf
        + f"--{boundary}--".encode() + crlf
    )

    req = urllib.request.Request(
        f"{EB_BASE_URL}/generate-brs",
        data=body_parts,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST"
    )

    print(f"Calling EB: {EB_BASE_URL}/generate-brs")
    with urllib.request.urlopen(req, timeout=300) as response:
        return json.loads(response.read().decode("utf-8"))


def handle_generate_code(body):
    brs_s3_path = body.get("brs_s3_path")
    repo_name = body.get("repo_name", "spring-batch-migration")

    if not brs_s3_path:
        raise ValueError("brs_s3_path is required")

    data = urllib.parse.urlencode({
        "brs_s3_path": brs_s3_path,
        "repo_name": repo_name
    }).encode("utf-8")

    req = urllib.request.Request(
        f"{EB_BASE_URL}/generate-code",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST"
    )

    print(f"Calling EB: {EB_BASE_URL}/generate-code")
    with urllib.request.urlopen(req, timeout=300) as response:
        return json.loads(response.read().decode("utf-8"))
