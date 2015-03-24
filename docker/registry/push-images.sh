#!/bin/bash
set -e

THIS_DIR="$(dirname $0)"

"${THIS_DIR}/../ship/push-images.sh" "${THIS_DIR}/ship.yml"
