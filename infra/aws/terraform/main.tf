data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_subnet" "application" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = var.availability_zone
  default_for_az    = true
}

resource "aws_db_subnet_group" "aidlink" {
  name       = "aidlink-db-subnets"
  subnet_ids = data.aws_subnets.default.ids
  tags       = { Name = "aidlink-db-subnets" }
}

data "aws_ami" "ubuntu_arm" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-arm64-server-*"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

resource "aws_security_group" "app" {
  name        = "aidlink-app-sg"
  description = "Public web access and restricted SSH for AIDLINK"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH from maintainer"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  ingress {
    description = "HTTP for Caddy certificate issuance"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP/3"
    from_port   = 443
    to_port     = 443
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aidlink-app-sg" }
}

resource "aws_security_group" "database" {
  name        = "aidlink-db-sg"
  description = "PostgreSQL access from the AIDLINK EC2 instance only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "PostgreSQL from application host"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aidlink-db-sg" }
}

resource "aws_db_instance" "aidlink" {
  identifier     = "aidlink"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password
  port     = 5432

  availability_zone    = var.availability_zone
  db_subnet_group_name = aws_db_subnet_group.aidlink.name
  multi_az             = false

  vpc_security_group_ids     = [aws_security_group.database.id]
  publicly_accessible        = false
  backup_retention_period    = 7
  backup_window              = "19:00-19:30"
  auto_minor_version_upgrade = true
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "aidlink-final"
  copy_tags_to_snapshot      = true

  lifecycle {
    prevent_destroy = true
  }

  tags = { Name = "aidlink-db" }
}

resource "aws_key_pair" "aidlink" {
  key_name   = "aidlink-key"
  public_key = var.ssh_public_key
}

resource "aws_instance" "aidlink" {
  ami                         = data.aws_ami.ubuntu_arm.id
  instance_type               = var.instance_type
  key_name                    = aws_key_pair.aidlink.key_name
  subnet_id                   = data.aws_subnet.application.id
  associate_public_ip_address = false
  vpc_security_group_ids      = [aws_security_group.app.id]

  user_data                   = file("${path.module}/../bootstrap-ec2.sh")
  user_data_replace_on_change = false

  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = { Name = "aidlink-app" }
}

resource "aws_eip" "aidlink" {
  domain   = "vpc"
  instance = aws_instance.aidlink.id

  tags = { Name = "aidlink-app-eip" }
}
