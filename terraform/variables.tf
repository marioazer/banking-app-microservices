

# the actual code
# terraform/variables.tf

variable "aws_region" {
  description = "AWS region for all infrastructure resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (e.g. dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "cluster_name" {
  description = "Name of the AWS EKS cluster"
  type        = string
  default     = "banking-eks-dev"
}

variable "db_password" {
  description = "Master password for the PostgreSQL RDS instance"
  type        = string
  sensitive   = true # Ensures password isn't exposed in plain text CLI outputs
}