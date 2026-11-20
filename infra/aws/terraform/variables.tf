variable "region" {
  description = "AWS region for EC2 and RDS"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "Local AWS CLI profile used by Terraform"
  type        = string
  default     = "default"
}

variable "instance_type" {
  description = "ARM EC2 instance type; t4g.small provides 2 GiB RAM"
  type        = string
  default     = "t4g.small"
}

variable "ssh_public_key" {
  description = "SSH public key installed on the EC2 instance"
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "IPv4 CIDR allowed to access SSH, normally your public IP with /32"
  type        = string
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "aidlink"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "aidlink_admin"
}

variable "db_password" {
  description = "PostgreSQL master password; keep it out of Git"
  type        = string
  sensitive   = true
}
