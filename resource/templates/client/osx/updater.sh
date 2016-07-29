#!/bin/bash
#DO NOT TOUCH THIS FILE! EDITING THIS FILE MAY RESULT IN DAMAGE TO YOUR SYSTEM!

if [ $# -ne 5 ]
then
  echo "Syntax: $0 [packageDir] [appDir] [updateFilepath] [updateVer] [username]"
  echo "Ex: $0 /Applications/[AEROFS_PRODUCT_SPACEFREE].app ~/Library/Application Support/AeroFSExec ~/Library/Application Support/AeroFS/update/AeroFSInstall-0.4.55.dmg 0.4.55 lisa"
  echo "NB. packageDir must NOT contain the trailing slash"
  exit 1
fi

PACKAGE_DIR="$1"
APP_DIR="$2"
UPDATE_FILEPATH="$3"
UPDATE_VER="$4"
USERNAME="$5"

if [ x"$PACKAGE_DIR" == x ]; then exit 1; fi

while ps -e -o pid,user,command | grep "$USERNAME" | grep [AEROFS_PRODUCT_SPACEFREE]Client | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep "$USERNAME" | grep [AEROFS_PRODUCT_SPACEFREE]Client | grep -v grep | awk '{ print $1 }')
  # builtin kill, when given a PID that is not valid, or no argument at all, will cause
  # the script to exit. Regardless of set -e status. And there is a possible race condition
  # no matter how we get the pid of the process - it could exit between the 'ps' and the
  # actual kill. Therefore we use /bin/kill whose error-handling is predictable.
  /bin/kill -9 $GET_PID
done

while ps -e -o pid,user,command | grep "$USERNAME" | grep aerofsd | grep -v grep > /dev/null; do
  GET_PID=$(ps -e -o pid,user,command | grep "$USERNAME" | grep aerofsd | grep -v grep | awk '{ print $1 }')
  # builtin kill, when given a PID that is not valid, or no argument at all, will cause
  # the script to exit. Regardless of set -e status. And there is a possible race condition
  # no matter how we get the pid of the process - it could exit between the 'ps' and the
  # actual kill. Therefore we use /bin/kill whose error-handling is predictable.
  /bin/kill -9 $GET_PID
done

TMPDIR="/tmp/aerofs-$UPDATE_VER-$USERNAME"
rm -rf "$TMPDIR"
mkdir -p "$TMPDIR"

if hdiutil -readonly -nobrowse -mountpoint "$TMPDIR" attach "$UPDATE_FILEPATH" ; then
  rm -rf "$PACKAGE_DIR/Contents"
  cp -af "$TMPDIR"/[AEROFS_PRODUCT_SPACEFREE].app/* "$PACKAGE_DIR"
  hdiutil detach "$TMPDIR"
else
  # wait until next update attempt
  rm -f "$UPDATE_FILEPATH"
fi

rm -rf "$TMPDIR"

touch "$APP_DIR"/../forceupdate

# NB: We cannot restart with nohup, because this will prevent OSX from stopping
# AeroFS on user logout. This would result in an "AeroFS is still running"
# dialog on the next login with the AeroFS tray icon not appearing despite the
# fact that AeroFS is running.
# NB: The -n and -F flags are important to make sure OSX doesn't "helpfully"
# opt not to launch the app because it is under the mistaken impression that
# there is already a running instance.
open -n -F -a "$PACKAGE_DIR"
