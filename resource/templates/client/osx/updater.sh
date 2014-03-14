#!/bin/bash
#DO NOT TOUCH THIS FILE! EDITING THIS FILE MAY RESULT IN DAMAGE TO YOUR SYSTEM!

if [ $# -ne 4 ]
then
  echo "Syntax: $0 [appDir] [updateFilepath] [updateVer] [username]"
  echo "Ex: $0 /Applications/[AEROFS_PRODUCT_SPACEFREE].app ~/Library/Application Support/AeroFS/update/aerofs-osx-0.4.55.zip 0.4.55 lisa"
  echo "NB. appDir must NOT contain the trailing slash"
  exit 1
fi

APPDIR="$1"
UPDATE_FILEPATH="$2"
UPDATE_VER="$3"
USERNAME="$4"

if [ x"$APPDIR" == x ]
then
  exit 1
fi

while ps -e -o pid,user,command | grep $USERNAME | grep [AEROFS_PRODUCT_SPACEFREE]Client | grep -v grep > /dev/null; do
  GET_PID=`ps -e -o pid,user,command | grep $USERNAME | grep [AEROFS_PRODUCT_SPACEFREE]Client | grep -v grep | awk '{ print $1 }'`
  # builtin kill, when given a PID that is not valid, or no argument at all, will cause
  # the script to exit. Regardless of set -e status. And there is a possible race condition
  # no matter how we get the pid of the process - it could exit between the 'ps' and the
  # actual kill. Therefore we use /bin/kill whose error-handling is predictable.
  /bin/kill -9 $GET_PID
done

while ps -e -o pid,user,command | grep $USERNAME | grep aerofsd | grep -v grep > /dev/null; do
  GET_PID=`ps -e -o pid,user,command | grep $USERNAME | grep aerofsd | grep -v grep | awk '{ print $1 }'`
  # builtin kill, when given a PID that is not valid, or no argument at all, will cause
  # the script to exit. Regardless of set -e status. And there is a possible race condition
  # no matter how we get the pid of the process - it could exit between the 'ps' and the
  # actual kill. Therefore we use /bin/kill whose error-handling is predictable.
  /bin/kill -9 $GET_PID
done

TMPDIR="/tmp/aerofs-$UPDATE_VER-$USERNAME"

rm -rf "$TMPDIR"

#if the unzip failed, avoid updating for now, force a new zip file to be downloaded
#on next update

unzip -q "$UPDATE_FILEPATH" -d "$TMPDIR"
if [ ! -d "$TMPDIR" ]; then
  rm -f "$UPDATE_FILEPATH"
else
  # Remove all files from the previous installation. Code signing will fail if there are any leftover files
  rm -rf "$APPDIR/Contents"
  cp -af "$TMPDIR"/Release/[AEROFS_PRODUCT_SPACEFREE].app/* "$APPDIR"
fi

rm -rf "$TMPDIR"

# NB: We cannot restart with nohup, because this will prevent OSX from stopping
# AeroFS on user logout. This would result in an "AeroFS is still running"
# dialog on the next login with the AeroFS tray icon not appearing despite the
# fact that AeroFS is running.
open -a "$APPDIR"
