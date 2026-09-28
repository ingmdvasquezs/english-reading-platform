variable "aws_region" {
  type        = string
  description = "AWS region for all deployed resources."
  default     = "us-east-1"
}

variable "environment" {
  type        = string
  description = "Deployment environment name (e.g. dev, staging, prod)."
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod", "test"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod, test."
  }
}

variable "project_name" {
  type        = string
  description = "Project identifier used in resource naming and tagging."
  default     = "english-reading"
}

variable "document_bucket_name" {
  type        = string
  description = "Explicit S3 bucket name for documents. If omitted (null), a deterministic name is computed from project and environment."
  default     = null
}

variable "allowed_frontend_origins" {
  type        = list(string)
  description = "Allowed CORS origins for direct browser PUT to S3."
  default     = ["http://localhost:4200"]

  validation {
    condition     = length(var.allowed_frontend_origins) > 0 && !contains(var.allowed_frontend_origins, "*")
    error_message = "allowed_frontend_origins must not be empty and must not contain wildcards ('*') for production security."
  }
}

variable "sqs_visibility_timeout_seconds" {
  type        = number
  description = "SQS main queue visibility timeout in seconds (default: 300s = 5 minutes)."
  default     = 300

  validation {
    condition     = var.sqs_visibility_timeout_seconds >= 30 && var.sqs_visibility_timeout_seconds <= 43200
    error_message = "sqs_visibility_timeout_seconds must be between 30 and 43200 (12 hours)."
  }
}

variable "sqs_main_message_retention_seconds" {
  type        = number
  description = "Message retention period for main queue in seconds (default: 345600 = 4 days)."
  default     = 345600

  validation {
    condition     = var.sqs_main_message_retention_seconds >= 60 && var.sqs_main_message_retention_seconds <= 1209600
    error_message = "sqs_main_message_retention_seconds must be between 60 and 1209600 (14 days)."
  }
}

variable "sqs_dlq_message_retention_seconds" {
  type        = number
  description = "Message retention period for DLQ in seconds (default: 1209600 = 14 days)."
  default     = 1209600

  validation {
    condition     = var.sqs_dlq_message_retention_seconds >= 60 && var.sqs_dlq_message_retention_seconds <= 1209600
    error_message = "sqs_dlq_message_retention_seconds must be between 60 and 1209600 (14 days)."
  }
}

variable "sqs_max_receive_count" {
  type        = number
  description = "Maximum deliveries before a message is sent to the Dead-Letter Queue (default: 5)."
  default     = 5

  validation {
    condition     = var.sqs_max_receive_count >= 1 && var.sqs_max_receive_count <= 1000
    error_message = "sqs_max_receive_count must be between 1 and 1000."
  }
}
