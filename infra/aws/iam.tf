# -----------------------------------------------------------------------------
# IAM Policy: API Process (Upload URL generation, verification & outbox dispatch)
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "api_document_imports" {
  statement {
    sid    = "AllowDirectUploadAndObjectVerification"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:GetObjectAttributes",
      "s3:DeleteObject"
    ]
    resources = [
      "${aws_s3_bucket.documents.arn}/documents/*"
    ]
  }

  statement {
    sid    = "AllowOutboxQueuePublishing"
    effect = "Allow"
    actions = [
      "sqs:SendMessage"
    ]
    resources = [
      aws_sqs_queue.document_imports.arn
    ]
  }
}

resource "aws_iam_policy" "api_document_imports" {
  name        = "${local.name_prefix}-api-document-imports"
  description = "Minimal IAM policy for API process: direct upload presigning, metadata verification, orphan purge and outbox SQS publishing."
  policy      = data.aws_iam_policy_document.api_document_imports.json
}

# -----------------------------------------------------------------------------
# IAM Policy: Worker Process (Queue polling, visibility heartbeat & source download)
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "worker_document_imports" {
  statement {
    sid    = "AllowQueueConsumptionAndHeartbeat"
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility"
    ]
    resources = [
      aws_sqs_queue.document_imports.arn
    ]
  }

  statement {
    sid    = "AllowPrivateSourceRead"
    effect = "Allow"
    actions = [
      "s3:GetObject"
    ]
    resources = [
      "${aws_s3_bucket.documents.arn}/documents/*"
    ]
  }
}

resource "aws_iam_policy" "worker_document_imports" {
  name        = "${local.name_prefix}-worker-document-imports"
  description = "Minimal IAM policy for Worker process: SQS message consumption, visibility extension heartbeat and source S3 read."
  policy      = data.aws_iam_policy_document.worker_document_imports.json
}
