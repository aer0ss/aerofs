#!/bin/bash
set -e
MODE_ROOT=$(dirname "${BASH_SOURCE[0]}")/modes
SRC_ROOT=$(dirname "${BASH_SOURCE[0]}")/../..

if [ $# -ne 1 ] && [ $# -ne 2 ]
then
    echo "Usage: $0 <mode> [-q]"
    echo
    echo "Available modes: public, private."
    echo
    echo "-q: quick: Skip network reconfiguration in the private mode. Useful when running this script repeatedly."
    echo
    echo "Note:"
    echo " - If running in private mode, the corresponding development"
    echo "   system must be running."

    exit 1
fi

MODE=$1

if [ "$2" = '-q' ]
then
    QUICK=1
else
    QUICK=0
fi

export STRIPE_PUBLISHABLE_KEY=pk_test_nlFBUMTVShdEAASKB0nZm6xf
export STRIPE_SECRET_KEY=sk_test_lqV5voHmrJZLom3iybJFSVqK

if [ "$MODE" = "private" ] && [ $QUICK -eq 0 ]
then
    echo ">>> Configuring your local prod to allow incoming connections..."
    pushd "$SRC_ROOT"/../packaging/bakery/development 1>/dev/null

    # the "sed 127.0.0.1..." below is so the config server listens to the
    # world so we can test license file uploads using locally deployed web
    # (see the Wiki page for setting up locally deployed web).
    vagrant ssh -c \
        "cd /etc/nginx/sites-available && \
        sudo sed -i 's/listen localhost:/listen /g' * && \
        sudo service nginx restart && \
        cd /opt/config && \
        sudo sed -i 's/127.0.0.1/0.0.0.0/g' *.py && \
        sudo service config restart && \
        sudo iptables --flush"
    popd 1>/dev/null
fi

cd "$MODE_ROOT"
~/.aerofs-web-env/bin/pserve --reload $MODE.ini
