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

# Kill the UI
# We need to grep for the product name in order to avoid killing other aerofs products
while ps -e -o pid,user,command | grep $USERNAME | grep aerofs.jar | grep [AEROFS_PRODUCT_UNIX]\\-bin | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep aerofs.jar | grep [AEROFS_PRODUCT_UNIX]\\-bin | grep -v grep | awk '{ print $1 }')
  kill -9 $GET_PID
done

# Kill the Daemon
# We can kill all daemons (even daemons belonging to other products) because they will be restarted by the UI
while ps -e -o pid,user,command | grep $USERNAME | grep aerofsd | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep aerofsd | grep -v grep | awk '{ print $1 }')
  kill -9 $GET_PID
done

# Unzip the update

TMPDIR="/tmp/[AEROFS_PRODUCT_UNIX]-$UPDATE_VER-$USERNAME"

rm -rf "$TMPDIR"
mkdir "$TMPDIR"
tar -zxf "$UPDATE_FILE_PATH" -C "$TMPDIR" 2>/dev/null >/dev/null

# If the untar failed, avoid updating for now; force a new tar file to be
# downloaded on next update attempt
if [ ! -d "$TMPDIR/aerofs" ]; then
  rm -f "$UPDATE_FILE_PATH"
else 
  # Remove all files from the previous installation, then copy the new one in.
  rm -rf "$APP_ROOT"
  mv "$TMPDIR"/aerofs "$APP_ROOT"
  if [ $? -ne 0 ]; then
    # Failed to place new root.  Maybe the disk was full, or some other
    # catastrophic failure took place. AeroFS will attempt to redownload on the
    # next user-started launch.
    rm -rf "$APP_ROOT"
    exit 1
  fi
  cd "$APP_ROOT" # Java doesn't like to run from deleted folders
fi

rm -rf "$TMPDIR"

if [ $GUI == "1" ]; then
  # the output redirections are needed so that the process won't terminate if
  # the child process (gui) outputs anything to stdout/stderr. We launch from
  # $APP_ROOT because the cli and gui may not be on the user's $PATH.
  "$APP_ROOT/[AEROFS_PRODUCT_UNIX]-gui" >/dev/null 2>&1
else
  nohup "$APP_ROOT/[AEROFS_PRODUCT_UNIX]-cli" >/dev/null 2>&1 &
fi
