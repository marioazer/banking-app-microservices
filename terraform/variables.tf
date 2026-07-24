

# the actual code
# terraform/variables.tf

# still learning terraform, variables here are basically input parameters for the whole config,
# referenced elsewhere as var.whatever_the_name_is, main.tf and provider.tf both do that a lot

variable "aws_region" {
  description = "AWS region for all infrastructure resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (e.g. dev, staging, prod)"
  type        = string
  # default means I can just run terraform apply with no extra flags and get dev, having a
  # default is what makes a variable optional instead of something terraform stops and asks for
  default = "dev"
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
  # learned sensitive just redacts the value from plan/apply console output and state show output,
  # it does not encrypt it, the raw value still sits in the state file, so state itself has to be
  # protected too, not something I fully appreciated until reading about it after the fact
}

# no default here on purpose, this has to be passed in through terraform.tfvars or -var on the
# command line, definitely do not want a real db password hardcoded and committed to the repo