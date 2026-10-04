# -----------------------------------------------------------------------------
# Amazon ECR Repository: Backend Docker Container Image
# -----------------------------------------------------------------------------
resource "aws_ecr_repository" "backend" {
  name                 = "${local.name_prefix}-backend"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Component = "backend-container-registry"
  }
}
