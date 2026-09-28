# -----------------------------------------------------------------------------
# Private Document Storage S3 Bucket
# -----------------------------------------------------------------------------
resource "aws_s3_bucket" "documents" {
  bucket = local.bucket_name
}

# -----------------------------------------------------------------------------
# S3 Security: Block All Public Access
# -----------------------------------------------------------------------------
resource "aws_s3_bucket_public_access_block" "documents" {
  bucket = aws_s3_bucket.documents.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# -----------------------------------------------------------------------------
# S3 Security: Bucket Owner Enforced (Disables ACLs entirely)
# -----------------------------------------------------------------------------
resource "aws_s3_bucket_ownership_controls" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# -----------------------------------------------------------------------------
# S3 Versioning: Suspended (Objects use server-owned immutable UUID keys)
# -----------------------------------------------------------------------------
resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id

  versioning_configuration {
    status = "Suspended"
  }
}

# -----------------------------------------------------------------------------
# S3 Encryption: Server-Side Encryption SSE-S3 (AES-256)
# -----------------------------------------------------------------------------
resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = false
  }
}

# -----------------------------------------------------------------------------
# S3 CORS: Direct Browser Upload (PUT) and Integrity Pre-Check (HEAD)
# -----------------------------------------------------------------------------
resource "aws_s3_bucket_cors_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  cors_rule {
    allowed_headers = [
      "Content-Type",
      "If-None-Match",
      "x-amz-checksum-sha256"
    ]
    allowed_methods = [
      "PUT",
      "HEAD"
    ]
    allowed_origins = var.allowed_frontend_origins
    expose_headers = [
      "ETag",
      "x-amz-checksum-sha256"
    ]
    max_age_seconds = 3600
  }
}

# -----------------------------------------------------------------------------
# S3 Bucket Policy: Enforce In-Transit Encryption (TLS 1.2+ Only)
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "documents_bucket_policy" {
  statement {
    sid     = "EnforceTLSRequestsOnly"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.documents.arn,
      "${aws_s3_bucket.documents.arn}/*"
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "documents" {
  bucket = aws_s3_bucket.documents.id
  policy = data.aws_iam_policy_document.documents_bucket_policy.json

  depends_on = [aws_s3_bucket_public_access_block.documents]
}
