#!/bin/bash
set -ex

THIS_DIR="$(dirname "$0")"

"${THIS_DIR}"//signup-decoder/stop.sh

"${THIS_DIR}"/signup-decoder/start.sh

"${THIS_DIR}"/dk-destroy.sh

"${THIS_DIR}"/../ci/modify-appliance.sh

"${THIS_DIR}"/emulate-ship.sh maintenance

"${THIS_DIR}"/setup.sh