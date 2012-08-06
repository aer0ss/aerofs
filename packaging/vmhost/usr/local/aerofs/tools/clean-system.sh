#!/bin/bash

echo "Deleting auto mount config and restarting..."
rm -f /etc/auto.master /etc/auto.aerofs
service autofs restart
sleep 5

echo "Deleting all local user data..."
rm -rf /mnt/share
