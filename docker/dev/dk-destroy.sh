#!/bin/bash

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"

"${THIS_DIR}/dk-crane.sh" rm -vf -dall maintenance
"${THIS_DIR}/dk-crane.sh" rm -vf -dall
