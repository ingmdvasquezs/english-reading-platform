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
