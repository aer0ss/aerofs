#!/bin/sh

if [ ! -f "$1/.nodeinfo"] ; then
  .venv/bin/devpi-init
fi

.venv/bin/devpi-server --host 0.0.0.0 --port 80 --serverdir $1