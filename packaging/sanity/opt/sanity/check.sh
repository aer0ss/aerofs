#!/bin/bash
# N.B. do not add set -e to this script, we do our own error checking.

PROBE_DIR=/opt/sanity/probes
if [ ! -d $PROBE_DIR ]
then
    echo "ERROR: probes directory $PROBE_DIR does not exist."
    exit 1
fi

cd /opt/sanity/probes

probes=$(ls *.sh 2>/dev/null)

if [ -z "$probes" ]
then
    echo "ERROR: no probes defined."
    exit 1
fi

failure=false
for probe in $probes
do
    msg=$(./$probe)

    if [ $? -ne 0 ]
    then
        echo "Probe failed: $(echo $probe | awk -F. '{print $1}'): $msg"
        failure=true
    fi
done

if [ "$failure" = "false" ]
then
    echo "SUCCESS!"
else
    exit 1
fi
