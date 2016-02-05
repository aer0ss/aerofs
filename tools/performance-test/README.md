# Terraform/Ansible Performance Test

Goal: make it easy to run performace test of the latest master from gerrit.

## Description

Terraform provisions one (large) instance for tha appliance and many instances
for the clients.

## Usage

### Setup

You'll need to perform some initial setup to connect terraform to AWS. First,
create a `terraform/terraform.tfvars` file with your access key, secret key,
and ssh key name, eg.

    aws_access_key = "XXXXXXXXXXXXXXXXXXXXXXX"
    aws_secret_key = "XXXXXXXX+XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    aws_ssh_key = "xxxxxx"

Modify `terraform/scale-test.tf` to set the instance sizes and counts for your
desired test.

You'll need to place your `aws.pem` file in the `ansible/` folder and a test
license as `ansible/roles/perftest.appliance/files/perftest.license`. We do not
use the standard test license here to allow for tests with more than 100 users.

Whenever any of the above variables change, you'll need to run:

    cd terraform
    terraform apply

For larger tests, you may wish to run `terraform apply --parallelism=NUM`. Note
that if NUM is too large, you may run into rate-limit issues with AWS.

### Running tests

To run the tests, you'll first need to build the environment:

    cd ansible
    ansible-playbook -i hosts build.yml

Then, you can start the tests with `ansible-playbook -i hosts test.yml`.

You'll want to let this run for a while to get a sense of the effect over time.
Depending on the speed of the appliance, this can take a long time (especially
if you are running to completion).

To stop the tests, kill the clients, and stop recording metrics, run
`ansible-playbook -i hosts stop_test.yml`.

### Collecting and Displaying Metrics

Once the test has started, a `metrics` folder will be created on the appliance.
This folder will be periodically updated with new metrics; the best way to get
these metrics is to run `make metrics`. This will rsync the metrics folder
onto your machine.

The `plotter.py` program can be used to plot various metrics against each
other. For some common metrics, run `make metrics.png`.
