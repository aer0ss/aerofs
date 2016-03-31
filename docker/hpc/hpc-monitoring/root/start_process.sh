#!/bin/sh

# Starting the crontab daemon
crond

# Starting the Monitoring API
python /main.py
