#!/bin/bash

rootDir="AeroFS Team Server Storage"
logFile="aerofsts.log"
tsInstalledMsg="Up and running"


die () {
    echo >&2 "$@"
    exit 1
}

runScTest() {
    startDate=`date`
    echo SC Test Started: $startDate
    echo $'\n'"$startDate: Test Started for $instancesPerUser instances per user, $numberOfFiles files per instance and $userCount users" > $logFileName
    echo "Total number of shared folders: $sharesCount, total files expected: $fileNumberGoal"$'\n' >> $logFileName
    START=$(date +%s)
    while true;
    do
        fileCount=$(find "$rootDir" -type f -not -name ".*" | grep -v "\.aerofs\." | wc -l)
        current=$(date +%s)
        currDiff=$(( $current - $START ))
        echo "$currDiff sec:"$'\t'"Files synced: $fileCount"
            echo "$currDiff sec:"$'\t'"Files synced: $fileCount" >> $logFileName
        if (($fileCount >= $fileNumberGoal )); then
            break
        fi
        sar -n DEV -u -b -r 1 1 | grep -v 'Average\|lo\|Linux' | head -n -3 >> $logFileName
    done
    END=$(date +%s)
    DIFF=$(( $END - $START ))
    echo "It took $DIFF seconds"
    echo $'\n'"$(date): Test finished in $DIFF seconds"$'\n' >> $logFileName
}

DIRECTORY='.aerofsts'

[ "$#" -eq 2 ] || die "2 arguments required (fileCount, userCount), $# provided"
echo $1 | grep -E -q '^[0-9]+$' || die "Numeric argument required, $1 provided"
echo $2 | grep -E -q '^[0-9]+$' || die "Numeric argument required, $2 provided"

instancesPerUser=1
numberOfFiles=$1
userCount=$2

sharesCount=$(($userCount*($userCount-1)/2))
fileNumberGoal=$(($instancesPerUser*$numberOfFiles*$sharesCount))
echo "Test Started for $instancesPerUser instances per user, $numberOfFiles files per instance and $userCount users"
echo "Total number of shared folders: $sharesCount, total files expected: $fileNumberGoal"
if [ -d "$DIRECTORY" ]; then
    # Cleanup
    echo Cleaning up
    killall java
    rm -rf AeroFS*
    rm -rf .aerofs*
    mkdir .aerofsts
    cp unattended-setup.properties .aerofsts/

    # prepare test results dir and file
    parentDir="testResults"
    mkdir -p $parentDir
    dirName=$parentDir"/sharedFoldersTsTest_"$instancesPerUser"inst_"$numberOfFiles"files_"$userCount"users_"$sharesCount"shares"
    mkdir -p $dirName

    # check next available ordinal for log file
    logFilePrefix="${dirName}/sctest_"
    logFileSuffix=".log"
    ordinal=1
    while [ -f  "$logFilePrefix$ordinal$logFileSuffix" ]
    do
        ordinal=$(($ordinal+1))
    done
    logFileName="$logFilePrefix$ordinal$logFileSuffix"

    echo "Installing and Starting AeroFS Team Server..."
    echo `date`: Starting AeroFS Team Server... > $logFile
    nohup aerofs/aerofsts-cli 0<&- &>> $logFile &
    tail -f $logFile | while read LOGLINE
    do
        echo $LOGLINE
        if [[ ${LOGLINE} == *"$tsInstalledMsg"* ]]; then
            pkill -P $$ tail
        fi
    done
    echo Starting Monitoring Data Directory
    runScTest
    echo Stopping Team Server...
    pid=$(pgrep java)
    kill $pid
    echo Test Completed. Detailed results can be found here: $logFileName
else
    echo Team Server is not installed. Please install Team Server first
fi