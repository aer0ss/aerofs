#!/bin/bash
set -e

echo "Setting fallback NTP servers..."
# The fallback list is adapted from CoreOS 575.0.0. Remember to update the default NTP server hardcoded in api.py
# When updating this list.
cat > /etc/systemd/timesyncd.conf.d/fallback.conf <<END
[Time]
FallbackNTP=0.coreos.pool.ntp.org 1.coreos.pool.ntp.org 2.coreos.pool.ntp.org 3.coreos.pool.ntp.org
END

echo "Starting API server..."
# -u to timely flush python output to docker logs
/container-scripts/restart-on-error python -u /api.py
