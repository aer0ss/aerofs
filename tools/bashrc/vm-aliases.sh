#!/bin/bash

# TODO (MP) for some reason we use delete here instead of kill...

VMTOOL=$(dirname "${BASH_SOURCE[0]}")/../vm-tool
if [[ ! -x $VMTOOL ]]; then
    echo "The vm-tool script (expected at $VMTOOL) was not found."
fi

# ------------------------------------------------------------------------------
# Modular VMs (persistent & transient)
# ------------------------------------------------------------------------------

alias modular-vms-create="$VMTOOL modular create"
alias modular-vms-start="$VMTOOL modular start"
alias modular-vms-halt="$VMTOOL modular halt"
alias modular-vms-delete="$VMTOOL modular kill"
alias modular-vms-ssh="$VMTOOL modular ssh"
alias modular-vms-kick="$VMTOOL modular kick"
alias modular-vms-deploy="$VMTOOL modular deploy"
alias modular-vms-deploy-all="$VMTOOL modular deploy-all"

# Local prod aliases, for backwards compatibility
alias lp-create=modular-vms-create
alias lp-start=modular-vms-start
alias lp-halt=modular-vms-halt
alias lp-delete=modular-vms-delete
alias lp-ssh=modular-vms-ssh
alias lp-kick=modular-vms-kick
alias lp-deploy=modular-vms-deploy
alias lp-deploy-all=modular-vms-deploy-all

# ------------------------------------------------------------------------------
# Unified VM
# ------------------------------------------------------------------------------

alias unified-vm-create="$VMTOOL unified create"
alias unified-vm-start="$VMTOOL unified start"
alias unified-vm-halt="$VMTOOL unified halt"
alias unified-vm-delete="$VMTOOL unified kill"
# Add the extra unified here because there is only one vm in unified
# configuration.
alias unified-vm-ssh="$VMTOOL unified ssh unified"
alias unified-vm-kick="$VMTOOL unified kick"
# FIXME (MP) pretty annoying that the user has to specify a box here. Specifying
# the box in the alias breaks the usage, though :(
alias unified-vm-deploy="$VMTOOL unified deploy"
alias unified-vm-deploy-all="$VMTOOL deploy-all unified unified"
