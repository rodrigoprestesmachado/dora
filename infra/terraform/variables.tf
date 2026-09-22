variable "aws_region" {
  description = <<-EOT
    AWS region to deploy into. Defaults to sa-east-1 (São Paulo) because the TJRS
    process lookup site (consulta.tjrs.jus.br) drops connections from US AWS IPs.
  EOT
  type        = string
  default     = "sa-east-1"
}

variable "project_name" {
  description = "Name/tag prefix used for all resources created by this stack."
  type        = string
  default     = "dora"
}

variable "instance_type" {
  description = <<-EOT
    EC2 instance type. t4g.* (Graviton/ARM) is cheaper than equivalent x86 (t3.*)
    instances and is enough to run dora + Postgres + Redis + Ollama (gemma4) on a
    single box. Bump to t4g.large if Ollama inference feels too slow/OOMs.
  EOT
  type        = string
  default     = "t4g.medium" # 2 vCPU / 4 GiB RAM
}

variable "root_volume_size_gb" {
  description = "Size (GiB) of the root EBS volume (OS + Docker images)."
  type        = number
  default     = 20
}

variable "data_volume_size_gb" {
  description = <<-EOT
    Size (GiB) of the extra EBS data volume used for Postgres data, Redis data and
    Ollama model weights (gemma4 alone is a few GB). Mounted at /data by user_data.sh.
  EOT
  type        = number
  default     = 40
}

variable "data_volume_type" {
  description = "EBS volume type for the data volume. gp3 is the cheapest general-purpose option."
  type        = string
  default     = "gp3"
}

variable "allowed_ssh_cidr_blocks" {
  description = <<-EOT
    Optional list of CIDR blocks allowed to SSH into the instance (port 22).
    Left empty by default — admin access is done via SSM Session Manager instead,
    so no inbound SSH port needs to be opened at all. Only set this if you specifically
    need SSH (e.g. for tooling that doesn't support SSM).
  EOT
  type        = list(string)
  default     = []
}

variable "vpc_id" {
  description = "VPC to deploy into. Leave null to use the account's default VPC."
  type        = string
  default     = null
}

variable "subnet_id" {
  description = "Subnet to deploy the instance into. Leave null to use the default VPC's default subnet."
  type        = string
  default     = null
}
