provider "aws" {
    access_key = "${var.aws_access_key}"
    secret_key = "${var.aws_secret_key}"
    region = "us-east-1"
}

resource "aws_instance" "appliance" {
    ami = "ami-05ffb06f"
    instance_type = "m3.2xlarge"
    key_name = "${var.aws_ssh_key}"
    vpc_security_group_ids = ["${aws_security_group.perftest.id}"]
    subnet_id = "${var.subnet.polaris_perftest}"
    user_data = "${file("appliance-cloud-config.yml")}"
    root_block_device {
        volume_size = 50
    }
    tags {
        Name = "SCTesting Appliance"
    }
}

resource "aws_instance" "client" {
    count = "50"
    ami = "ami-d05e75b8"
    instance_type = "t2.micro"
    key_name = "${var.aws_ssh_key}"
    vpc_security_group_ids = ["${aws_security_group.perftest.id}"]
    subnet_id = "${var.subnet.polaris_perftest}"
    root_block_device {
        volume_size = 8
    }
    tags {
        Name = "SCTesting Client"
    }
    associate_public_ip_address = "true"
}

resource "aws_security_group" "perftest" {
  name = "perftest"
  description = "For polaris performance testing"
  vpc_id = "${var.vpc.production}"

  ingress {
    from_port = 0
    to_port = 22
    protocol = "tcp"
    # VPC access only
    cidr_blocks = ["172.16.0.0/16"]
  }

  ingress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    # Within subnet only
    cidr_blocks = ["172.16.8.0/22"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_eip" "appliance" {
  instance = "${aws_instance.appliance.id}"
  vpc = true
}
