#!/bin/bash
set -e

crond -f

# Make this container run forever.
tail -f /dev/null

