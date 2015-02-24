#!/bin/bash
set -ex
#
# This script runs the appliance's setup procedure as if a user interacts with the appliance's Web pages.
#
# Precondition: the appliance is running at the specified IP and hasn't been set up yet.
#
# The script exits 0 only if the test succeeds.
#

if [ $# != 4 ]; then
    (set +x
        echo "Usage: $0 <hostname> <ip> <reboot-flag-file> <create-first-user>"
        echo "       <hostname> <ip> hostname & ip of the appliance under test. Hostname must be consistent with the"
        echo "                      CNAME of the browser cert defined in setup.yml otherwise tests would fail. The"
        echo "                      hostname-to-ip mapping does NOT need to match DNS or /etc/hosts."
        echo "       <screenshots_output_dir> where the script will dump screenshots to"
        echo "       <create-first-user> 'true' to create the first admin user. It requires the Sign-up Decoder service"
        echo "                      (see ci-cloud-config.jinja)."
        echo "       <reboot-flag-file> the program writes a non-empty string to the reboot flag file to indicate that"
        echo "                      it expects the AeroFS containers to reboot to the default group."
    )
    exit 11
fi
HOST="$1"
IP="$2"
REBOOT_FLAG_FILE="$3"
CREATE_FIRST_USER="$4"

# Docker requires absolute paths. OSX has no realpath command.
THIS_DIR="$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)"
REBOOT_FLAG_FILE="$(cd "$(dirname "${REBOOT_FLAG_FILE}")" && pwd)/$(basename "${REBOOT_FLAG_FILE}")"

SCREEN_SHOTS="${THIS_DIR}/../../../out.shell/screenshots/bunker-setup"
mkdir -p "${SCREEN_SHOTS}"
rm -rf "${SCREEN_SHOTS}"/*

docker build -t aerofs/test.bunker.setup "${THIS_DIR}"

(set +e
    docker run --rm \
        -v "${THIS_DIR}/../../../packaging/bakery/development/test.license":/test.license \
        -v "${SCREEN_SHOTS}":/screenshots \
        -v "${REBOOT_FLAG_FILE}":/reboot-flag \
        --add-host "${HOST}:${IP}" \
        aerofs/test.bunker.setup python -u main.py \
        ${HOST} /screenshots /test.license /reboot-flag ${CREATE_FIRST_USER}
    EXIT_CODE=$?

    (set +x
        [[ ${EXIT_CODE} = 0 ]] && RES=SUCCESS || RES=FAILED
        echo
        echo ">>> ${RES}. Screenshots are at ${SCREEN_SHOTS}"
        echo
    )

    exit ${EXIT_CODE}
)