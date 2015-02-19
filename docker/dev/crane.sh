#!/bin/bash
THIS_DIR=$(dirname "${BASH_SOURCE[0]}")
YML="$(cd "${THIS_DIR}/.." && pwd)/crane.yml"
(set -ex; crane $@ -c "${YML}")
