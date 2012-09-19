#!/bin/bash 

# DO NOT TOUCH THIS FILE! EDITING THIS FILE MAY RESULT IN DAMAGE TO YOUR SYSTEM!

if [ $# -ne 5 ]; then
  # not enough arguments
  exit 1
fi

APP_ROOT="$1"
UPDATE_FILE_PATH="$2"
UPDATE_VER="$3"
USERNAME="$4"
GUI="$5"

if [ x"$APP_ROOT" == x ]; then
  # Application directory is empty
  exit 1
fi

while ps -e -o pid,user,command | grep $USERNAME | grep aerofs.jar | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep aerofs.jar | grep -v grep | awk '{ print $1 }')
  kill -9 $GET_PID
done

while ps -e -o pid,user,command | grep $USERNAME | grep aerofsd | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep aerofsd | grep -v grep | awk '{ print $1 }')
  kill -9 $GET_PID
done

# Unzip the update

TMPDIR="/tmp/aerofs-$UPDATE_VER-$USERNAME"

rm -rf "$TMPDIR"
mkdir "$TMPDIR"
tar -zxf "$UPDATE_FILE_PATH" -C "$TMPDIR" 2>/dev/null >/dev/null

# If the untar failed, avoid updating for now; force a new tar file to be
# downloaded on next update attempt
if [ ! -d "$TMPDIR/aerofs" ]; then
  rm -f "$UPDATE_FILE_PATH"
else 
  # copy over the update
  cp -rf "$TMPDIR"/aerofs/* "$APP_ROOT/"
fi

rm -rf "$TMPDIR"

# run aerofs
if [ $GUI == "1" ]; then
  # the output redirections are needed so that the process won't terminate if
  # the child process (aerofs-gui) outputs anything to stdout/stderr.
  # We launch from $APP_ROOT because aerofs-cli and aerofs-gui may not be on
  # the user's $PATH
  "$APP_ROOT/aerofs-gui" >/dev/null 2>&1
else
  nohup "$APP_ROOT/aerofs-cli" >/dev/null 2>&1 &
fi
