#!/bin/bash
set -e

aws_key=$(cat /host/aws.key)
secret_key=$(cat /host/aws.secret)

sed -i -e "s/{{ accesskey }}/$aws_key/g" /config.yml
sed -i -e "s/{{ secretkey }}/$secret_key/g" /config.yml

cp /config.yml /etc/docker/registry/config.yml
registry serve /etc/docker/registry/config.yml
