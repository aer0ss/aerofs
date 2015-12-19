#!/bin/bash
set -e
#
# Precondition: the appliance is running at the specified hostname and hasn't been set up yet.
#
if [ $# != 2 ]; then
    echo "Usage: $0 <host> <create-first-user>"
    echo "       <host> hostname of the appliance under test"
    echo "       <create-first-user> 'true' to create the first admin user. It requires the Sign-up Decoder service"
    echo "                      (see ci-cloud-config.jinja)."
    exit 11
fi
HOST="$1"
CREATE_FIRST_USER="$2"

# Docker requires absolute paths. OSX has no realpath command.
THIS_DIR="$(cd $(dirname "$0") && pwd)"

${THIS_DIR}/../../webdriver-lib/test-driver.sh ${THIS_DIR} ${HOST} -- ${CREATE_FIRST_USER}
