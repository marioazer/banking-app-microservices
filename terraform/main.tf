# the actual code for the terraform main file
# code for terraform/main.tf

# still learning terraform, so notes below are partly me working out how each block actually behaves

# this is the vpc network module, using the official terraform-aws-modules vpc module instead
# of hand rolling every subnet and route table myself
# module vs resource was confusing at first, a module is really just someone else's reusable
# bundle of resources, source points at where to pull it from and version pins which release

module "vpc" {
  source = "terraform-aws-modules/vpc/aws"

  version = "~> 5.0"

  name = "banking-vpc"
  cidr = "10.0.0.0/16"

  # spreading across two azs for basic availability, not going wider than that for this project
  # the ${var.aws_region}a syntax is string interpolation, just stitching the region variable
  # together with a literal "a" or "b" to get a real availability zone name like us-east-1a
  azs = ["${var.aws_region}a", "${var.aws_region}b"]

  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]

  # separate dedicated subnets just for rds, keeps the database off the same subnets as the eks nodes
  database_subnets             = ["10.0.201.0/24", "10.0.202.0/24"]
  create_database_subnet_group = true


  # single nat gateway to keep cost down for now, a real prod setup would want one per az for redundancy
  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true

  # these tags are what let the aws load balancer controller auto discover which subnets to use
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# db security group, this is the firewall rule layer that decides who can even reach the database
# this is a plain resource block instead of a module, so its a single aws object, not a bundle
# of many, the two part name below is <resource type> then <the local name I chose for it>
resource "aws_security_group" "rds_sg" {
  name        = "banking-rds-sg"
  description = "Allow inbound traffic from EKS worker nodes to PostgreSQL RDS"

  # module.vpc.vpc_id is how one resource reaches into another module's output, terraform
  # figures out the vpc has to get created before this security group can reference it
  vpc_id = module.vpc.vpc_id

  # only the eks worker node security group can reach postgres, nothing else in or outside the vpc
  ingress {
    description = "PostgreSQL from EKS"
    from_port   = 5432
    to_port     = 5432

    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  # wide open egress, the db just needs to be able to respond and reach out for things like patches
  egress {
    description = "Allow all outbound traffic"

    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# aws rds postgres database, this is the real managed database behind the DB_HOST value in the k8s configmap
resource "aws_db_instance" "banking_db" {
  identifier = "banking-postgres-db"

  # starts small with room to autoscale storage up to 40gb as data grows, instead of over provisioning up front
  allocated_storage     = 10
  max_allocated_storage = 40
  engine                = "postgres"
  engine_version        = "15.4"
  instance_class        = "db.t3.micro"
  db_name               = "banking"
  username              = "dbadmin"
  # var.db_password pulls from the sensitive variable defined in variables.tf, never a literal
  # string here, this way the real password only ever lives in terraform.tfvars, not in this file
  password               = var.db_password
  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  # fine for a dev/learning project, skipping the final snapshot on destroy, would flip this for real prod data
  skip_final_snapshot = true
  # never publicly reachable, only the eks nodes inside the vpc can get to it through the security group above
  publicly_accessible = false
}

# aws eks kubernetes cluster module, this is what all six services actually run on top of
# another registry module rather than a bare resource, same source/version pattern as the vpc one above
module "eks" {
  source = "terraform-aws-modules/eks/aws"

  version = "~> 19.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.30"

  # public endpoint access so kubectl works from my own machine without a vpn or bastion host set up
  cluster_endpoint_public_access = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # one managed node group, scales between 2 and 4 nodes depending on load, starting at 2 to keep cost down
  eks_managed_node_groups = {
    primary_nodes = {
      min_size     = 2
      max_size     = 4
      desired_size = 2

      instance_types = ["t3.medium"]
      capacity_type  = "ON_DEMAND"
    }
  }
}

