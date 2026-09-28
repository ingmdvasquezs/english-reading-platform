# AWS Infrastructure Foundation — Durable Document Imports (Phase 17.5C)

This directory contains the Terraform configuration for the foundation AWS cloud resources required by the durable document import subsystem:
- **Private Amazon S3 Bucket:** Private, encrypted (SSE-S3 AES-256), TLS-enforced, with CORS configured for direct browser upload.
- **Amazon SQS Queues:** Standard main queue (`english-reading-<env>-document-imports`) with long polling (20s) and 300s visibility timeout, paired with a Dead-Letter Queue (DLQ) with 14-day retention and redrive allow policy.
- **IAM Managed Policies:** Scoped, least-privilege IAM policies for the **API** process (upload presigning, verification, orphan purge, outbox publishing) and the **Worker** process (queue polling, visibility heartbeat extension, source download).

---

## 1. Prerequisites

- **Terraform:** `>= 1.5.0` installed.
- **AWS CLI:** Installed and authenticated with permissions to manage S3, SQS, and IAM.

---

## 2. Directory Structure

```text
infra/aws/
├── versions.tf               # Terraform and AWS provider constraints
├── providers.tf              # AWS provider setup and default tags
├── variables.tf              # Input variables with validation rules
├── locals.tf                 # Deterministic resource naming and tags
├── s3.tf                     # S3 bucket, public access block, SSE, CORS, TLS policy
├── sqs.tf                    # SQS main queue, DLQ, redrive policy, SSE
├── iam.tf                    # Scoped IAM policies for API and Worker
├── outputs.tf                # Resource identifiers, URLs, and policy ARNs
├── terraform.tfvars.example  # Example variable values for development
└── README.md                 # This documentation
```

---

## 3. Terraform Lifecycle Commands

### A. Initialization
Initializes the provider plugins without a remote backend:
```bash
terraform init -backend=false
```

### B. Formatting & Validation
Check formatting compliance and syntax:
```bash
terraform fmt -check -recursive
terraform validate
```

### C. Execution Plan (Preview)
Review planned infrastructure changes against AWS without applying:
```bash
terraform plan -var-file=terraform.tfvars.example
```

> [!WARNING]
> **DO NOT RUN `terraform apply`** without explicit team authorization. Resources should only be created in designated AWS accounts following organizational approval.

---

## 4. Remote State Recommendation (Production)

For team collaboration and production deployments, configure a remote state backend with state locking. Example configuration to add to `versions.tf`:

```hcl
terraform {
  backend "s3" {
    bucket         = "english-reading-terraform-state-<account-id>"
    key            = "foundation/document-imports/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "english-reading-terraform-locks"
    encrypt        = true
  }
}
```

---

## 5. Application Environment Variable Mapping

Once infrastructure outputs are generated, inject them into the respective container/host environments:

### API Process (`APP_ROLE=api`)
| Environment Variable | Source / Terraform Output | Description |
| :--- | :--- | :--- |
| `APP_ROLE` | `api` | Runs REST/SOAP API and ScheduledOutboxDispatcher |
| `AWS_REGION` | `output.aws_region` | AWS region |
| `DOCUMENT_IMPORT_QUEUE_URL` | `output.document_import_queue_url` | SQS queue for transactional outbox dispatcher |
| `DOCUMENT_IMPORT_QUEUE_REGION`| `output.aws_region` | Region of the SQS queue |
| `DOCUMENT_IMPORT_OUTBOX_ENABLED` | `true` | Enables outbox polling and publishing |
| `APP_DOCUMENTSTORAGE_S3_BUCKET` | `output.document_bucket_name` | S3 bucket for upload presigning and verification |
| `APP_DOCUMENTSTORAGE_S3_REGION` | `output.aws_region` | S3 region |

### Worker Process (`APP_ROLE=worker`)
| Environment Variable | Source / Terraform Output | Description |
| :--- | :--- | :--- |
| `APP_ROLE` | `worker` | Runs SqsDocumentImportConsumer and processor |
| `AWS_REGION` | `output.aws_region` | AWS region |
| `DOCUMENT_IMPORT_QUEUE_URL` | `output.document_import_queue_url` | SQS queue to consume messages from |
| `DOCUMENT_IMPORT_QUEUE_REGION`| `output.aws_region` | Region of the SQS queue |
| `DOCUMENT_IMPORT_WORKER_ENABLED` | `true` | Enables worker polling loop |
| `APP_DOCUMENTSTORAGE_S3_BUCKET` | `output.document_bucket_name` | S3 bucket to download source files from |
| `APP_DOCUMENTSTORAGE_S3_REGION` | `output.aws_region` | S3 region |

---

## 6. S3 Environment Variable Gap Analysis (Backend Audit)

The audit of `S3DocumentStorageProperties.java` and `application.yaml` revealed that `app.document-storage.s3.bucket` does not currently define an explicit placeholder like `${DOCUMENT_STORAGE_S3_BUCKET:}` in `application.yaml`.

Until explicit property placeholders are introduced in a future configuration phase, Spring Boot can bind these properties via:
1. **Relaxed Environment Variables:**
   - `APP_DOCUMENTSTORAGE_S3_BUCKET=<bucket_name>`
   - `APP_DOCUMENTSTORAGE_S3_REGION=<region>`
2. **Spring Application JSON:**
   - `SPRING_APPLICATION_JSON='{"app":{"document-storage":{"s3":{"bucket":"<bucket>","region":"<region>"}}}}'`
3. **JVM System Properties:**
   - `-Dapp.document-storage.s3.bucket=<bucket> -Dapp.document-storage.s3.region=<region>`
