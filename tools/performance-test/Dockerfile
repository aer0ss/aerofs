FROM ubuntu:15.04

# Common tools
RUN apt-get update && apt-get install -y \
    curl \
    dnsutils \
    git \
    graphviz \
    jq \
    python-crypto \
    python-httplib2 \
    python-jinja2 \
    python-paramiko \
    python-setuptools \
    python-support \
    python-yaml \
    silversearcher-ag \
    sshpass \
    unzip \
    vim \
    wget

# Terraform
RUN cd /usr/local/bin &&\
    curl -L https://releases.hashicorp.com/terraform/0.6.6/terraform_0.6.6_linux_amd64.zip -o terraform.zip &&\
    unzip terraform.zip &&\
    rm terraform.zip

# Ansible
RUN wget https://launchpad.net/~ansible/+archive/ubuntu/ansible/+files/ansible_2.0.0.2-1ppa~vivid_all.deb -O ansible.deb &&\
    dpkg -i ansible.deb &&\
    rm -f ansible.deb

# Terraform-inventory (to connect Terraform to Ansible)
RUN cd /usr/local/bin &&\
    curl -L https://github.com/adammck/terraform-inventory/releases/download/v0.6/terraform-inventory_0.6_linux_amd64.zip -o terraform-inventory.zip &&\
    unzip terraform-inventory.zip &&\
    rm terraform-inventory.zip

# Vault
RUN cd /usr/local/bin &&\
    curl -L https://releases.hashicorp.com/vault/0.2.0/vault_0.2.0_linux_amd64.zip -o vault.zip &&\
    unzip vault.zip &&\
    rm vault.zip

ENV VAULT_ADDR http://vault.arrowfs.org:8200

ADD ansible/requirements.yml /
RUN ansible-galaxy install -r /requirements.yml
