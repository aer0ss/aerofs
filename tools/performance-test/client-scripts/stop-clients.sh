#!/bin/bash
set -e

ps aux | grep aerofs | grep -v grep | awk {'print $2'} | xargs kill
