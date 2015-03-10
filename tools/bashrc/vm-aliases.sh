#!/bin/bash

VMTOOL=$(dirname "${BASH_SOURCE[0]:-$0}")/../vm-tool
if [[ ! -x $VMTOOL ]]; then
    echo "The vm-tool script (expected at $VMTOOL) was not found."
fi

alias lp-create="$VMTOOL create"
alias lp-start="$VMTOOL start"
alias lp-halt="$VMTOOL halt"
# Avoid lp-kill as it is often misused by novice users (i.e. WW) when they attempt to shutdown the VM.
alias lp-destroy="$VMTOOL kill"
alias lp-kick="$VMTOOL kick"
alias lp-deploy="$VMTOOL deploy"
alias lp-ssh="$VMTOOL ssh"
alias lp-bootstrap="$VMTOOL bootstrap"
