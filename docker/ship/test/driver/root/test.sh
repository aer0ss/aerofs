#!/bin/bash
set -ex

[[ $# == 2 ]] || {
    echo "Usage: $0 <aws_access_key> <aws_secret_key>"
    exit 11
}

cat > terraform.tfvars <<END
aws_access_key = "$1"
aws_secret_key = "$2"
END

echo ">>> Terraform applying ..."
terraform apply terraform

set +e
/test-upgrade.sh $(terraform output ip)
EXIT=$?
set -e

set +x
if [ ${EXIT} = 0 ]; then
    echo ">>> Terraform destroying ..."
    terraform destroy -force terraform
else
    echo "ERROR: test failed. Please log in to core@$(terraform output ip) to investigate and then manully destroy"
    echo "the EC2 instance $(terraform output instance_id) and security group $(terraform output security_group_id)."
    echo "Without the destruction the next run of the test would fail."
fi

exit ${EXIT}