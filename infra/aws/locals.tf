data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # S3 bucket name: use explicit name if provided, otherwise compute deterministic name
  bucket_name = coalesce(var.document_bucket_name, "${local.name_prefix}-documents")

  main_queue_name = "${local.name_prefix}-document-imports"
  dlq_queue_name  = "${local.name_prefix}-document-imports-dlq"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Component   = "document-import-infrastructure"
  }
}
