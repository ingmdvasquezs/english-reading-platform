# -----------------------------------------------------------------------------
# Phase 17.5G2: EC2 Single-Host Runtime (Graviton t4g.small)
# -----------------------------------------------------------------------------

# Dynamic lookup of latest Amazon Linux 2023 ARM64 AMI
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# -----------------------------------------------------------------------------
# IAM Role and Instance Profile for EC2 Single Host (APP_ROLE=all)
# -----------------------------------------------------------------------------

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    sid     = "AllowEC2AssumeRole"
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2_runtime" {
  name               = "${local.name_prefix}-ec2-runtime-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-ec2-runtime-role"
  })
}

# 1. SSM Session Manager policy for console/CLI access without SSH or open port 22
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 2. Amazon ECR Read-Only policy to pull container images
resource "aws_iam_role_policy_attachment" "ecr_read" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# 3. Existing API document-import IAM policy
resource "aws_iam_role_policy_attachment" "api_policy" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = aws_iam_policy.api_document_imports.arn
}

# 4. Existing Worker document-import IAM policy
resource "aws_iam_role_policy_attachment" "worker_policy" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = aws_iam_policy.worker_document_imports.arn
}

# 5. Scoped policy for retrieving RDS master password from AWS Secrets Manager
data "aws_iam_policy_document" "ec2_secretsmanager" {
  statement {
    sid    = "AllowGetRdsSecretValue"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue"
    ]
    resources = [
      aws_db_instance.postgres.master_user_secret[0].secret_arn
    ]
  }
}

resource "aws_iam_policy" "ec2_secretsmanager" {
  name        = "${local.name_prefix}-ec2-secretsmanager"
  description = "Scoped policy allowing EC2 runtime to retrieve RDS master password from AWS Secrets Manager."
  policy      = data.aws_iam_policy_document.ec2_secretsmanager.json
}

resource "aws_iam_role_policy_attachment" "secretsmanager_attachment" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = aws_iam_policy.ec2_secretsmanager.arn
}

# 6. Scoped policy for retrieving application secrets from AWS SSM Parameter Store
data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

data "aws_iam_policy_document" "ec2_ssm_parameters" {
  statement {
    sid    = "AllowGetDevParameters"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters"
    ]
    resources = [
      "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/*"
    ]
  }

  statement {
    sid    = "AllowDecryptWithSsmKmsKey"
    effect = "Allow"
    actions = [
      "kms:Decrypt"
    ]
    resources = [
      data.aws_kms_alias.ssm.target_key_arn
    ]
  }
}

resource "aws_iam_policy" "ec2_ssm_parameters" {
  name        = "${local.name_prefix}-ec2-ssm-parameters"
  description = "Scoped policy allowing EC2 runtime to retrieve and decrypt parameters under /${var.project_name}/${var.environment}/*"
  policy      = data.aws_iam_policy_document.ec2_ssm_parameters.json
}

resource "aws_iam_role_policy_attachment" "ssm_parameters_attachment" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = aws_iam_policy.ec2_ssm_parameters.arn
}

resource "aws_iam_instance_profile" "ec2_runtime" {
  name = "${local.name_prefix}-ec2-instance-profile"
  role = aws_iam_role.ec2_runtime.name

  tags = local.common_tags
}

# -----------------------------------------------------------------------------
# Persistent EBS Volume for Cover Assets
# -----------------------------------------------------------------------------

resource "aws_ebs_volume" "assets" {
  availability_zone = "${var.aws_region}a"
  size              = var.ebs_volume_size
  type              = "gp3"
  encrypted         = true

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-assets-volume"
  })
}

resource "aws_volume_attachment" "assets" {
  device_name = "/dev/xvdf"
  volume_id   = aws_ebs_volume.assets.id
  instance_id = aws_instance.backend.id
}

# -----------------------------------------------------------------------------
# EC2 Instance
# -----------------------------------------------------------------------------

resource "aws_instance" "backend" {
  ami           = data.aws_ssm_parameter.al2023_arm64.value
  instance_type = var.ec2_instance_type

  subnet_id                   = aws_subnet.public_a.id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  associate_public_ip_address = true

  iam_instance_profile = aws_iam_instance_profile.ec2_runtime.name

  # Root block device configuration
  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true

    tags = merge(local.common_tags, {
      Name = "${local.name_prefix}-root-volume"
    })
  }

  # Security: Enforce IMDSv2 and allow 2 hops for Docker container IAM credential delegation
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "enabled"
  }

  user_data = templatefile("${path.module}/templates/user_data.sh.tftpl", {
    aws_region                = var.aws_region
    project_name              = var.project_name
    environment               = var.environment
    ebs_volume_id             = aws_ebs_volume.assets.id
    rds_secret_arn            = aws_db_instance.postgres.master_user_secret[0].secret_arn
    rds_endpoint              = aws_db_instance.postgres.endpoint
    rds_db_name               = var.rds_db_name
    app_domain                = var.app_domain
    image_tag                 = var.backend_image_tag
    ecr_repository_url        = aws_ecr_repository.backend.repository_url
    document_bucket_name      = aws_s3_bucket.documents.id
    document_import_queue_url = aws_sqs_queue.document_imports.url
    azure_translator_region   = var.azure_translator_region
  })

  # Ensure all IAM policies are attached to the runtime role before EC2 boots and runs user_data
  depends_on = [
    aws_iam_role_policy_attachment.ssm_core,
    aws_iam_role_policy_attachment.ecr_read,
    aws_iam_role_policy_attachment.api_policy,
    aws_iam_role_policy_attachment.worker_policy,
    aws_iam_role_policy_attachment.secretsmanager_attachment,
    aws_iam_role_policy_attachment.ssm_parameters_attachment
  ]

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-backend"
  })
}
