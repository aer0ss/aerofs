#!/bin/bash
set -eu

if [ $# -ne 1 ]
then
    echo >&2 "Usage: $0 <VM>"
    exit 1
fi

VM=$1
THIS_DIR="$( cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -z "$(colima list | grep ${VM})" ]
then
  # https://github.com/abiosoft/colima
  colima start "${VM}" \
    --cpu 2 --memory 3 --disk 50 \
    --network-address

  ip=$(colima status -p "${VM}" --json | jq -r .ip_address)

  # sigh... need to stop/start to make the DNS resolvable to
  # the auto-assigned IP address
  # see https://github.com/abiosoft/colima/issues/1232
  colima stop -p "${VM}"
  colima start -p "${VM}" --dns-host "share.syncfs.com=$ip"

  echo "starting package cache..."
  $THIS_DIR/../../tools/cache/start.sh
fi
