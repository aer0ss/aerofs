#!/bin/bash
set +e

# Return the value of the given key specified in ship.yml

SHIP_YML="$1"
shift

grep "^$1:" "${SHIP_YML}" | sed -e "s/^$1: *//" | sed -e 's/ *$//'
