# -----------------------------------------------------------------------------
# Phase 17.5G2: RDS PostgreSQL Single-AZ Instance
# -----------------------------------------------------------------------------

resource "aws_db_instance" "postgres" {
  identifier     = "${local.name_prefix}-postgres"
  engine         = "postgres"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  db_name  = var.rds_db_name
  username = var.rds_username

  # Zero plaintext master password in Terraform configuration or state
  manage_master_user_password = true

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  multi_az            = false
  skip_final_snapshot = true # dev-tier deployment; enables clean destruction without blocking

  backup_retention_period    = 7
  backup_window              = "03:00-04:00"
  maintenance_window         = "Sun:04:30-Sun:05:30"
  auto_minor_version_upgrade = true

  deletion_protection = false

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-postgres"
  })
}
