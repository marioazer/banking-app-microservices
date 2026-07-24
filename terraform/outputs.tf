# these are the values terraform prints after apply, mostly so I don't have to go dig them up in the
# aws console every time, and deploy-to-eks.yml relies on the cluster name to run update-kubeconfig
# still learning terraform, but the pattern clicked fast, output just exposes a value that already
# exists somewhere in the resources/modules above, it does not create anything new on its own

output "vpc_id" {
  description = "The ID of the provisioned AWS VPC"

  value = module.vpc.vpc_id
}

# real db host, this is what actually goes into DB_HOST once its not a placeholder anymore
# reaching into aws_db_instance.banking_db here since that resource comes straight from a
# resource block in main.tf, not a module, so no module. prefix needed to get at its attributes
output "rds_endpoint" {
  description = "The connection endpoint for the PostgreSQL database"
  value       = aws_db_instance.banking_db.endpoint
}

output "eks_cluster_name" {
  description = "The name of the provisioned EKS cluster"
  value       = module.eks.cluster_name
}

# useful for double checking the kubernetes provider block below is actually pointed at the right cluster
output "eks_cluster_endpoint" {
  description = "The API server endpoint for the EKS cluster"

  value = module.eks.cluster_endpoint
}