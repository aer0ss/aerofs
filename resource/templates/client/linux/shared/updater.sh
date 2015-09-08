#!/bin/bash
#DO NOT TOUCH THIS FILE! EDITING THIS FILE MAY RESULT IN DAMAGE TO YOUR SYSTEM!

if [ $# -ne 5 ]
then
  echo "Syntax: $0 [appDir] [updateFilepath] [updateVer] [username] [is_gui]"
  echo "Ex: $0 ~/.aerofs-bin/current ~/.aerofs/update/aerofs-linux-0.4.55.zip 0.4.55 lisa 1"
  echo "NB. appDir must NOT contain the trailing slash"
  exit 1
fi

APP_DIR="$1"
UPDATE_FILEPATH="$2"
UPDATE_VER="$3"
USERNAME="$4"
GUI="$5"

if [ x"$APP_DIR" == x ]; then exit 1; fi

while ps -e -o pid,user,command | grep "$USERNAME" | grep aerofs.jar | grep [AEROFS_PRODUCT_UNIX]\\-bin | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep aerofs.jar | grep [AEROFS_PRODUCT_UNIX]\\-bin | grep -v grep | awk '{ print $1 }')
  kill -9 $GET_PID
done

while ps -e -o pid,user,command | grep "$USERNAME" | grep aerofsd | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep aerofsd | grep -v grep | awk '{ print $1 }')
  kill -9 $GET_PID
done

TMPDIR="/tmp/aerofs-$UPDATE_VER-$USERNAME"
rm -rf "$TMPDIR"

mkdir "$TMPDIR"
tar -zxf "$UPDATE_FILEPATH" -C "$TMPDIR" 2>/dev/null >/dev/null
if [ ! -d "$TMPDIR/aerofs" ]; then
  # wait until next update attempt
  rm -f "$UPDATE_FILEPATH"
else
  cp -a "$TMPDIR"/aerofs/* "$APP_DIR"
  if [ $? -ne 0 ]; then
    # Failed to place new root. Maybe the disk was full, or some other
    # catastrophic failure took place. AeroFS will attempt to redownload on the
    # next user-started launch.
    rm -rf "$TMPDIR"
    exit 1
  fi
fi

rm -rf "$TMPDIR"

rm -f "$APP_DIR"/../manifest.json

if [ $GUI == "1" ]; then
  # the output redirections are needed so that the process won't terminate if
  # the child process (gui) outputs anything to stdout/stderr. We launch from
  # $APP_DIR because the cli and gui may not be on the user's $PATH.
  "$APP_DIR/[AEROFS_PRODUCT_UNIX]-gui" >/dev/null 2>&1
else
  nohup "$APP_DIR/[AEROFS_PRODUCT_UNIX]-cli" >/dev/null 2>&1 &
fi
