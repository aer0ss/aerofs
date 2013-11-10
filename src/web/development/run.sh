#!/bin/bash
set -e
MODE_ROOT=$(dirname "${BASH_SOURCE[0]}")/modes
SRC_ROOT=$(dirname "${BASH_SOURCE[0]}")/../..

if [ $# -ne 1 ]
then
    echo "Usage: $0 <mode>"
    echo
    echo "Available modes: public, private."
    echo
    echo "Note:"
    echo " - If running in private mode, the corresponding development"
    echo "   system must be running."

    exit 1
fi

MODE=$1

export STRIPE_PUBLISHABLE_KEY=pk_test_nlFBUMTVShdEAASKB0nZm6xf
export STRIPE_SECRET_KEY=sk_test_lqV5voHmrJZLom3iybJFSVqK

if [ "$MODE" = "private" ]
then
    echo ">>> Configuring your local prod to allow incoming connections..."
    pushd "$SRC_ROOT"/../packaging/bakery/development 1>/dev/null
    vagrant ssh -c\
        "cd /etc/nginx/sites-available && \
        sudo sed -i 's/localhost://g' * && \
        sudo service nginx restart && \
        sudo iptables --flush"
    popd 1>/dev/null
fi

cd "$MODE_ROOT"
~/env/bin/pserve $MODE.ini
