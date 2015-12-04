#!/bin/bash

# AeroFS Developer Environment Profile
# This file should be sourced in your .bash_profile or .zshrc
# When making changes to this file, please make sure it works for both bash and zsh on OS X and Linux.

if [ -f /usr/libexec/java_home ]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v '1.8*')"
fi

source $(dirname "${BASH_SOURCE[0]:-$0}")/docker.sh
source $(dirname "${BASH_SOURCE[0]:-$0}")/invoke.sh
