FROM ubuntu:15.04

MAINTAINER AeroFS "support@aerofs.com"

# Install Common tools
RUN apt-get update && \
    apt-get install -y \
        curl \
        jq \
        unzip

# Install Terraform
RUN cd /usr/local/bin && \
    curl -L https://dl.bintray.com/mitchellh/terraform/terraform_0.6.0_linux_amd64.zip -o terraform.zip && \
    unzip terraform.zip && \
    rm terraform.zip

COPY root /
