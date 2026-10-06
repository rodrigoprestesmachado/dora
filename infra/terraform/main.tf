####
# Low-cost, single-EC2-instance infrastructure for dora.
#
# Everything (app + Postgres/pgvector + Redis) runs as Docker Compose services on
# one Graviton (ARM) EC2 instance. Production chat and embeddings call OpenAI;
# Ollama is only used in local development. No RDS, ElastiCache or ALB — those
# are the main cost drivers we're avoiding here. HTTPS termination is handled by
# Caddy (see ../../Caddyfile) directly on the instance, and admin access is done
# via SSM Session Manager instead of an SSH key pair, so the security group does
# not need an open port 22.
####

data "aws_vpc" "selected" {
  id      = var.vpc_id
  default = var.vpc_id == null ? true : null
}

data "aws_subnets" "selected" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.selected.id]
  }
}

locals {
  subnet_id = coalesce(var.subnet_id, data.aws_subnets.selected.ids[0])
}

# Amazon Linux 2023, arm64 — matches the t4g (Graviton) instance family.
data "aws_ami" "al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-arm64"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ------------------------------------------------------------------
# Security group — only 80/443 inbound (Caddy). No SSH port by default;
# use SSM Session Manager for shell access (see outputs.tf).
# ------------------------------------------------------------------
resource "aws_security_group" "dora" {
  name        = "${var.project_name}-sg"
  description = "dora: allow HTTP/HTTPS from the internet, admin access via SSM only"
  vpc_id      = data.aws_vpc.selected.id

  ingress {
    description = "HTTP (Caddy ACME challenge + redirect to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS (Caddy)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  dynamic "ingress" {
    for_each = length(var.allowed_ssh_cidr_blocks) > 0 ? [1] : []
    content {
      description = "Optional SSH (only if allowed_ssh_cidr_blocks is set)"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.allowed_ssh_cidr_blocks
    }
  }

  egress {
    description = "Allow all outbound (image pulls, OpenAI/WhatsApp APIs, scraping)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-sg"
    Project = var.project_name
  }
}

# ------------------------------------------------------------------
# IAM role — grants SSM Session Manager access only. No other AWS
# permissions are needed since everything lives on the single instance.
# ------------------------------------------------------------------
resource "aws_iam_role" "dora_instance" {
  name = "${var.project_name}-instance-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = {
    Project = var.project_name
  }
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.dora_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "dora_instance" {
  name = "${var.project_name}-instance-profile"
  role = aws_iam_role.dora_instance.name
}

# ------------------------------------------------------------------
# EC2 instance
# ------------------------------------------------------------------
resource "aws_instance" "dora" {
  ami                    = data.aws_ami.al2023_arm64.id
  instance_type          = var.instance_type
  subnet_id              = local.subnet_id
  vpc_security_group_ids = [aws_security_group.dora.id]
  iam_instance_profile   = aws_iam_instance_profile.dora_instance.name
  user_data              = file("${path.module}/user_data.sh")
  # Re-run user_data if it changes (Terraform otherwise ignores user_data updates
  # on existing instances). Remove this if you don't want instance replacement on
  # every script tweak.
  user_data_replace_on_change = true

  root_block_device {
    volume_size           = var.root_volume_size_gb
    volume_type           = "gp3"
    delete_on_termination = true
  }

  metadata_options {
    http_tokens = "required" # enforce IMDSv2
  }

  # T4g launches as "unlimited" and bills surplus CPU credits. "standard" throttles
  # past the baseline instead, which is the cheaper mode for this single box.
  credit_specification {
    cpu_credits = "standard"
  }

  tags = {
    Name    = var.project_name
    Project = var.project_name
  }
}

# Extra EBS volume for Postgres, Redis and Docker's data-root, kept independent
# from the root volume/instance lifecycle.
resource "aws_ebs_volume" "data" {
  availability_zone = aws_instance.dora.availability_zone
  size              = var.data_volume_size_gb
  type              = var.data_volume_type

  tags = {
    Name    = "${var.project_name}-data"
    Project = var.project_name
  }
}

resource "aws_volume_attachment" "data" {
  device_name = "/dev/sdf" # surfaces as /dev/nvme1n1 on Nitro instances (t4g); handled in user_data.sh
  volume_id   = aws_ebs_volume.data.id
  instance_id = aws_instance.dora.id
}

# ------------------------------------------------------------------
# Elastic IP — stable public address to point DNS at.
# ------------------------------------------------------------------
resource "aws_eip" "dora" {
  instance = aws_instance.dora.id
  domain   = "vpc"

  tags = {
    Name    = "${var.project_name}-eip"
    Project = var.project_name
  }
}
