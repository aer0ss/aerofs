#!/bin/bash
set -eu

ip=$(colima status docker-dev --json | jq -r .ip_address)

# TODO: support non-macOS
syncfs_ip=$(dscacheutil -q host -a name share.syncfs.com | grep ip_address | cut -d' ' -f2)

if [[ "${ip}" == "${syncfs_ip}" ]] ; then
  echo "share.syncfs.com at ${ip}"
else
  echo "updating /etc/hosts : share.syncfs.com -> ${ip}"
  echo
  echo "NB: this may prompt for root password..."
  echo
  if grep -F share.syncfs.com /etc/hosts >/dev/null ; then
    sudo sed -i '' '/share\.syncfs\.com/d' /etc/hosts
  fi
  echo "${ip} share.syncfs.com" | sudo tee -a /etc/hosts
fi
