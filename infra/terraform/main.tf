# Terraform configuration for Conexus Caller ID on Linode LKE
terraform {
  required_version = ">= 1.6.0"
  required_providers {
    linode = {
      source  = "linode/linode"
      version = "~> 2.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {
    bucket         = "conexus-terraform-state"
    key            = "caller-id/prod/terraform.tfstate"
    region         = "us-east-1"
    endpoint       = "https://s3.us-east-1.linodeobjects.com"
    skip_credentials_validation = true
    skip_metadata_api_check     = true
  }
}

provider "linode" {
  token = var.linode_token
}

provider "kubernetes" {
  host                   = linode_lke_cluster.caller_id.kubeconfig[0].host
  client_certificate     = base64decode(linode_lke_cluster.caller_id.kubeconfig[0].client_certificate)
  client_key             = base64decode(linode_lke_cluster.caller_id.kubeconfig[0].client_key)
  cluster_ca_certificate = base64decode(linode_lke_cluster.caller_id.kubeconfig[0].cluster_ca_certificate)
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

# Variables
variable "linode_token" {
  type        = string
  description = "Linode API token"
  sensitive   = true
}

variable "cloudflare_api_token" {
  type        = string
  description = "Cloudflare API token"
  sensitive   = true
}

variable "domain_name" {
  type        = string
  default     = "api.conexus-systems.com.br"
  description = "Domain for the API"
}

variable "environment" {
  type        = string
  default     = "production"
  description = "Environment name"
}

variable "region" {
  type        = string
  default     = "us-east"
  description = "Linode region"
}

variable "k8s_version" {
  type        = string
  default     = "1.28"
  description = "Kubernetes version"
}

# Random suffix for unique names
resource "random_id" "suffix" {
  byte_length = 4
}

# LKE Cluster
resource "linode_lke_cluster" "caller_id" {
  label       = "conexus-caller-id-${var.environment}-${random_id.suffix.hex}"
  region      = var.region
  k8s_version = var.k8s_version

  pool {
    label            = "general"
    type             = "g6-standard-2"
    count            = 3
    autoscaler_enabled = true
    min_count        = 2
    max_count        = 10
  }

  pool {
    label            = "worker"
    type             = "g6-standard-4"
    count            = 2
    autoscaler_enabled = true
    min_count        = 1
    max_count        = 5
    taints = [
      {
        key    = "workload"
        value  = "caller-id"
        effect = "NoSchedule"
      }
    ]
  }
}

# Kubernetes namespace
resource "kubernetes_namespace" "caller_id" {
  metadata {
    name = "conexus-caller-id-${var.environment}"
    labels = {
      "environment" = var.environment
      "app"         = "caller-id"
    }
  }
}

# PostgreSQL (Linode Managed Database)
resource "linode_database" "postgres" {
  label           = "conexus-caller-id-pg-${random_id.suffix.hex}"
  region          = var.region
  type            = "g6-standard-2"
  engine          = "postgresql"
  version         = "16"
  username        = "callerid"
  password        = random_password.pg_password.result
  allow_list      = [linode_lke_cluster.caller_id.pools[0].nodes[0].id]
  encrypted       = true
  backup_enabled  = true
}

resource "random_password" "pg_password" {
  length  = 32
  special = false
}

# Redis (Linode Managed Database)
resource "linode_database" "redis" {
  label           = "conexus-caller-id-redis-${random_id.suffix.hex}"
  region          = var.region
  type            = "g6-standard-1"
  engine          = "redis"
  version         = "7"
  username        = "default"
  password        = random_password.redis_password.result
  allow_list      = [linode_lke_cluster.caller_id.pools[0].nodes[0].id]
  encrypted       = true
}

resource "random_password" "redis_password" {
  length  = 32
  special = false
}

# Object Storage bucket for datasets
resource "linode_object_storage_bucket" "datasets" {
  label       = "conexus-caller-id-datasets-${random_id.suffix.hex}"
  region      = var.region
  cluster     = "us-east-1"
  acl         = "private"
  cors_enabled = true
}

# Object Storage keys
resource "linode_object_storage_key" "datasets" {
  label       = "caller-id-datasets-key"
  bucket_id   = linode_object_storage_bucket.datasets.id
  access_key  = random_string.access_key.result
  secret_key  = random_password.obj_secret.result
}

resource "random_string" "access_key" {
  length  = 20
  special = false
  upper   = true
  number  = true
}

resource "random_password" "obj_secret" {
  length  = 40
  special = false
}

# Cloudflare DNS
resource "cloudflare_record" "api" {
  zone_id = cloudflare_zone.main.id
  name    = "api"
  value   = linode_lke_cluster.caller_id.pools[0].nodes[0].ip
  type    = "A"
  ttl     = 300
  proxied = true
}

resource "cloudflare_zone" "main" {
  zone = "conexus-systems.com.br"
}

# Outputs
output "kubeconfig" {
  value     = linode_lke_cluster.caller_id.kubeconfig[0].raw_config
  sensitive = true
}

output "postgres_connection" {
  value     = "postgresql+asyncpg://callerid:${random_password.pg_password.result}@${linode_database.postgres.host}:5432/conexus_caller_id"
  sensitive = true
}

output "redis_connection" {
  value     = "redis://default:${random_password.redis_password.result}@${linode_database.redis.host}:6379/0"
  sensitive = true
}

output "object_storage_endpoint" {
  value = linode_object_storage_bucket.datasets.endpoint
}

output "object_storage_bucket" {
  value = linode_object_storage_bucket.datasets.label
}