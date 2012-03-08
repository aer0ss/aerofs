#!/bin/bash

BASE="`dirname $BASH_SOURCE`"

GIT_ROOT=`git rev-parse --show-toplevel`
if [ "$?" != "0" ]; then
    echo "Not in a valid git repository" 1>&2
    return 1
fi

cp -p ${BASE}/git_hooks/gerrit-commit-msg ${GIT_ROOT}/.git/hooks/commit-msg
echo "Added Gerrit Change-ID commit hook"
