#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 8080
/opt/sanity/probes/tools/url.sh https://localhost:4433/sp
