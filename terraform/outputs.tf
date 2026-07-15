output "vpc_id" {
  description = "The ID of the provisioned AWS VPC"

  value = module.vpc.vpc_id
}

output "rds_endpoint" {
  description = "The connection endpoint for the PostgreSQL database"
  value       = aws_db_instance.banking_db.endpoint
}

output "eks_cluster_name" {
  description = "The name of the provisioned EKS cluster"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "The API server endpoint for the EKS cluster"

  value = module.eks.cluster_endpoint
}