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

# -----------------------------------------------------------------------------
# Phase 17.5G2: EC2 & RDS Runtime Variables
# -----------------------------------------------------------------------------

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the dedicated VPC."
  default     = "10.0.0.0/16"
}

variable "ec2_instance_type" {
  type        = string
  description = "EC2 Graviton instance type for single-host runtime."
  default     = "t4g.small"
}

variable "ebs_volume_size" {
  type        = number
  description = "Size of persistent gp3 EBS volume in GiB for document cover assets."
  default     = 10

  validation {
    condition     = var.ebs_volume_size >= 10 && var.ebs_volume_size <= 100
    error_message = "ebs_volume_size must be between 10 and 100 GiB."
  }
}

variable "rds_instance_class" {
  type        = string
  description = "RDS DB instance class."
  default     = "db.t4g.small"
}

variable "rds_engine_version" {
  type        = string
  description = "PostgreSQL engine version for Amazon RDS."
  default     = "17.5"
}

variable "rds_allocated_storage" {
  type        = number
  description = "Allocated storage for RDS PostgreSQL in GiB."
  default     = 20
}

variable "rds_max_allocated_storage" {
  type        = number
  description = "Maximum storage limit for RDS PostgreSQL autoscaling in GiB."
  default     = 50
}

variable "rds_db_name" {
  type        = string
  description = "PostgreSQL database name."
  default     = "english_reading"
}

variable "rds_username" {
  type        = string
  description = "Master username for PostgreSQL database."
  default     = "english_user"
}

variable "backend_image_tag" {
  type        = string
  description = "Docker image tag in ECR to deploy on EC2."
  default     = "394fc2d-b1"
}

variable "app_domain" {
  type        = string
  description = "Application domain for Caddy. Default ':80' serves HTTP on dev IP without domain cert error."
  default     = ":80"
}

variable "azure_translator_region" {
  type        = string
  description = "Azure Translator service region."
  default     = "eastus2"
}
