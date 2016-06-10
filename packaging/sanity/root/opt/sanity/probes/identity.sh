#!/bin/bash
set -e
/opt/sanity/probes/tools/port.sh identity.service 8080 "identity servlet"
