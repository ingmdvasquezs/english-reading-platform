output "aws_region" {
  description = "Configured AWS region."
  value       = var.aws_region
}

output "document_bucket_name" {
  description = "Name of the private S3 bucket for documents."
  value       = aws_s3_bucket.documents.id
}

output "document_bucket_arn" {
  description = "ARN of the private S3 bucket for documents."
  value       = aws_s3_bucket.documents.arn
}

output "document_import_queue_name" {
  description = "Name of the main SQS Standard queue for document imports."
  value       = aws_sqs_queue.document_imports.name
}

output "document_import_queue_url" {
  description = "URL of the main SQS Standard queue for document imports."
  value       = aws_sqs_queue.document_imports.url
}

output "document_import_queue_arn" {
  description = "ARN of the main SQS Standard queue for document imports."
  value       = aws_sqs_queue.document_imports.arn
}

output "document_import_dlq_name" {
  description = "Name of the Dead-Letter Queue (DLQ) for document imports."
  value       = aws_sqs_queue.document_imports_dlq.name
}

output "document_import_dlq_arn" {
  description = "ARN of the Dead-Letter Queue (DLQ) for document imports."
  value       = aws_sqs_queue.document_imports_dlq.arn
}

output "api_iam_policy_arn" {
  description = "ARN of the IAM managed policy for the API process."
  value       = aws_iam_policy.api_document_imports.arn
}

output "worker_iam_policy_arn" {
  description = "ARN of the IAM managed policy for the Worker process."
  value       = aws_iam_policy.worker_document_imports.arn
}

output "ecr_repository_name" {
  description = "Name of the backend ECR repository."
  value       = aws_ecr_repository.backend.name
}

output "ecr_repository_url" {
  description = "URL of the backend ECR repository."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_repository_arn" {
  description = "ARN of the backend ECR repository."
  value       = aws_ecr_repository.backend.arn
}

# -----------------------------------------------------------------------------
# Phase 17.5G2: EC2 & RDS Runtime Outputs
# -----------------------------------------------------------------------------

output "vpc_id" {
  description = "ID of the dedicated VPC."
  value       = aws_vpc.main.id
}

output "ec2_instance_id" {
  description = "Instance ID of the backend EC2 host."
  value       = aws_instance.backend.id
}

output "ec2_public_ip" {
  description = "Public IPv4 address of the backend EC2 host (diagnostics only, not an application entrypoint; use SSM port forwarding)."
  value       = aws_instance.backend.public_ip
}


output "rds_endpoint" {
  description = "Connection endpoint of the PostgreSQL RDS instance."
  value       = aws_db_instance.postgres.endpoint
}

output "rds_port" {
  description = "Port of the PostgreSQL RDS instance."
  value       = aws_db_instance.postgres.port
}

output "ec2_runtime_role_name" {
  description = "IAM role name assigned to the EC2 runtime instance profile."
  value       = aws_iam_role.ec2_runtime.name
}
