variable "aws_access_key" {}
variable "aws_secret_key" {}

provider "aws" {
    access_key = "${var.aws_access_key}"
    secret_key = "${var.aws_secret_key}"
    region = "us-east-1"
}

resource "aws_instance" "test" {
    # A CoreOS AMI
    ami = "ami-9d5894f6"
    instance_type = "t1.micro"
    user_data = "${file("/build/cloud-config.yml")}"
    security_groups = [ "${aws_security_group.test.name}" ]
    tags {
        Name = "Ship Enterprise Test"
    }
}

resource "aws_security_group" "test" {
    name = "ship-enterprise-test"
    ingress {
        from_port = 0
        to_port = 65535
        protocol = "tcp"
        cidr_blocks = ["0.0.0.0/0"]
    }
}

output "instance_id" {
    value = "${aws_instance.test.id}"
}

output "security_group_id" {
    value = "${aws_security_group.test.id}"
}

output "ip" {
    value = "${aws_instance.test.public_ip}"
}
