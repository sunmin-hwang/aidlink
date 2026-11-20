output "public_ip" {
  description = "Elastic IP to assign to the deployment domain"
  value       = aws_eip.aidlink.public_ip
}

output "ssh_command" {
  description = "SSH command for the EC2 host"
  value       = "ssh ubuntu@${aws_eip.aidlink.public_ip}"
}

output "database_endpoint" {
  description = "Private RDS endpoint"
  value       = aws_db_instance.aidlink.address
}

output "jdbc_url" {
  description = "AIDLINK_DB_URL value for .env.aws"
  value       = "jdbc:postgresql://${aws_db_instance.aidlink.address}:${aws_db_instance.aidlink.port}/${var.db_name}?sslmode=require"
}
