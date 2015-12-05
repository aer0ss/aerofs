#!/bin/bash
THIS_DIR=$(dirname "${BASH_SOURCE[0]}")
python "$THIS_DIR/gen-crane-yml.py"
YML="$(cd "${THIS_DIR}/.." && pwd)/crane.yml"
(set -ex; crane $@ -c "${YML}")
