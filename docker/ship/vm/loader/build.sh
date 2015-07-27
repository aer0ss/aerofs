#!/bin/bash
set -ex

docker build -t shipenterprise/vm-loader "$(dirname "$0")"