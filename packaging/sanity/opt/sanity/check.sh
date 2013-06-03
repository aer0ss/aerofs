#!/bin/bash
cd /opt/sanity/probes

probes=$(ls *.sh 2>/dev/null)

if [ -z "$probes" ]
then
    echo "Error: no probes defined."
    exit 1
fi

for probe in $probes
do
    ./$probe

    if [ $? -ne 0 ]
    then
        echo "Error: probe failed: $(echo $probe | awk -F. '{print $1}')"
        exit 1
    fi
done
