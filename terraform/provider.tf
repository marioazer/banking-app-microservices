# the actual code for terraform provider
# terraform/provider.tf

# still getting comfortable with terraform, this file is where it declares which providers (plugins
# that actually know how to talk to aws, kubernetes, etc) it needs and pins their versions
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
  }
}

# provider blocks are where I actually configure each plugin above, this one is for aws itself
provider "aws" {
  region = var.aws_region

  # default_tags automatically stamps every single aws resource this provider touches with these
  # tags, learned this beats manually adding a tags block to every resource one by one
  default_tags {
    tags = {
      Environment = var.environment
      Project     = "BankingMicroservices"
      ManagedBy   = "Terraform"
    }
  }
}

# second provider config, this is the interesting one, it lets this same terraform run also
# talk directly to the eks cluster it just created, all in one apply instead of a separate step
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

  # instead of a static kubeconfig token, this shells out to the aws cli to fetch a short lived
  # eks token on demand, so credentials never need to be stored or rotated by hand
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
  }
}