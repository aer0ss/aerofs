#!/bin/bash

LPTOOL=$(dirname "${BASH_SOURCE[0]}")/../lptool
if [[ ! -x $LPTOOL ]]; then
    echo "The lptool script (expected at $LPTOOL) is not found."
fi

alias lp-create="$LPTOOL create"
alias lp-delete="$LPTOOL delete"
alias lp-halt="$LPTOOL halt"
alias lp-start="$LPTOOL start"
alias lp-ssh="$LPTOOL ssh"
alias lp-kick="$LPTOOL kick"
alias lp-kick-all="$LPTOOL kick-all"
alias lp-deploy="$LPTOOL deploy"
alias lp-deploy-all="$LPTOOL deploy-all"
alias lptool=$LPTOOL
