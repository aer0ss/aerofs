#!/bin/bash
set -e
cd $(dirname "${BASH_SOURCE[0]}")/modes

if [ $# -ne 1 ]
then
    echo "Usage: $0 <mode>"
    echo
    echo "Available modes: prod, modular, unified."
    echo
    echo "Note:"
    echo " - If running in unified or modular mode, the corresponding development"
    echo "   system must be running."

    exit 1
fi

MODE=$1

export STRIPE_PUBLISHABLE_KEY=pk_test_nlFBUMTVShdEAASKB0nZm6xf
export STRIPE_SECRET_KEY=sk_test_lqV5voHmrJZLom3iybJFSVqK

~/env/bin/pserve $MODE.ini
