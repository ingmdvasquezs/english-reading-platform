# -----------------------------------------------------------------------------
# SQS Dead-Letter Queue (Created first so main queue can reference its ARN)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "document_imports_dlq" {
  name                      = local.dlq_queue_name
  message_retention_seconds = var.sqs_dlq_message_retention_seconds
  sqs_managed_sse_enabled   = true
}

# -----------------------------------------------------------------------------
# SQS Main Queue: Document Import Work Requests (Standard Queue)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "document_imports" {
  name                       = local.main_queue_name
  visibility_timeout_seconds = var.sqs_visibility_timeout_seconds
  receive_wait_time_seconds  = 20
  message_retention_seconds  = var.sqs_main_message_retention_seconds
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.document_imports_dlq.arn
    maxReceiveCount     = var.sqs_max_receive_count
  })
}

# -----------------------------------------------------------------------------
# SQS DLQ Redrive Allow Policy (Restricts redrive source strictly to main queue)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue_redrive_allow_policy" "document_imports_dlq" {
  queue_url = aws_sqs_queue.document_imports_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.document_imports.arn]
  })
}
