#!/bin/sh

if [ ! -f "$1/.nodeinfo" ] ; then
  .venv/bin/devpi-init --serverdir $1
fi

.venv/bin/devpi-server --host 0.0.0.0 --port 80 --serverdir $1