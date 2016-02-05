variable "aws_access_key" {}
variable "aws_secret_key" {}
variable "aws_ssh_key" {}

variable "subnet" {
  default = {
    internal_production = "subnet-2ff94b42"
    polaris_perftest = "subnet-f2b77aaa"
  }
}

variable "vpc" {
  default = {
    production = "vpc-06f84a6b"
  }
}

variable "zone" {
  default = {
    arrowfs = "ZQ0K8S7X1RFHV"
  }
}
