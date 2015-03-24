#!/bin/bash
set -e

export SETTINGS_FLAVOR=s3
export AWS_REGION=us-east-1
export AWS_BUCKET=registry.aerofs.com
export STORAGE_PATH=/data
export AWS_KEY="$(cat /host/aws.key)"
export AWS_SECRET="$(cat /host/aws.secret)"

docker-registry
