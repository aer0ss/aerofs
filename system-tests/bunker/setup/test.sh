#!/bin/bash
#
# Precondition: the appliance is running at the specified IP and hasn't been set up yet.
#
if [ $# != 3 ]; then
    echo "Usage: $0 <ip> <reboot-flag-file> <create-first-user>"
    echo "       <ip> IP of the appliance under test"
    echo "       <create-first-user> 'true' to create the first admin user. It requires the Sign-up Decoder service"
    echo "                      (see ci-cloud-config.jinja)."
    echo "       <reboot-flag-file> the program writes a non-empty string to the reboot flag file to indicate that"
    echo "                      it expects the AeroFS containers to reboot to the default group."
    exit 11
fi
IP="$1"
REBOOT_FLAG_FILE="$2"
CREATE_FIRST_USER="$3"

# Docker requires absolute paths. OSX has no realpath command.
THIS_DIR="$(cd $(dirname "$0") && pwd)"
REBOOT_FLAG_FILE="$(cd "$(dirname "${REBOOT_FLAG_FILE}")" && pwd)/$(basename "${REBOOT_FLAG_FILE}")"

${THIS_DIR}/../../webdriver-lib/test-wrapper.sh ${THIS_DIR} ${IP} \
    -v "${REBOOT_FLAG_FILE}":/reboot-flag -- ${CREATE_FIRST_USER}