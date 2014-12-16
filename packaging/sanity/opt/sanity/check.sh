#!/bin/bash
# N.B. do not add set -e to this script, we do our own error checking.

PROBES_DIR=/opt/sanity/probes

function check_for_probes_dir()
{
    if [ ! -d $PROBES_DIR ]
    then
        echo "ERROR: probes directory $PROBES_DIR does not exist."
        exit 1
    fi
}

function get_probe_scripts()
{
    local probes=$(ls *.sh 2>/dev/null)

    if [ -z "$probes" ]
    then
        echo "ERROR: no probes defined."
        exit 2
    fi

    echo $probes
}

check_for_probes_dir
cd $PROBES_DIR
probe_scripts=$(get_probe_scripts)
probes_to_ignore_names=()

while getopts "hi:" opt
do
    case $opt in
        i)
            probes_to_ignore_names+=($OPTARG)
            ;;
        h)
            echo "Usage: $0 [-i <probe_to_ignore_name>]"
            exit 0
            ;;
        ?)
            echo "Invalid option. Exiting."
            exit 3
            ;;
        esac
done

failure=false
for probe_script in $probe_scripts
do
    probe_name=${probe_script%%\.*}
    msg=$(./$probe_script)

    # Skip the probs the user has asked us to ignore.
    ignore=false
    for probe_to_ignore in ${probes_to_ignore_names[*]}
    do
        if [ "$probe_name" == "$probe_to_ignore" ]; then ignore=true && break; fi
    done
    if [ $ignore = true ]; then continue; fi

    # Run the probe script, i.e. execute the service sanity test.
    if [ $? -ne 0 ]
    then
        echo "Probe failed: $probe_name: $msg"
        failure=true
        # Do not break; we want to document all failures.
    fi
done

if [ "$failure" = "false" ]
then
    echo "SUCCESS!"
else
    exit 4
fi
