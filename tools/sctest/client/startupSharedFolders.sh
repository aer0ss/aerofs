#!/bin/bash

invitationsWaitTime=120
logFile="aerofs.log"
DIRECTORY='.aerofs'
installedMsg="Up and running"
emailDomain="aerofs.com"

function generateDummy {
    local folder="AeroFS/$1"
    mkdir -p $folder
    for  ((x=0; x<$fileCount; ++x))
    do
        uuid=`uuidgen`
        head -c $fileSize /dev/urandom > $folder/dummy$x"_"$uuid
    done;
}

function displayUsageAndExit {
    echo "!!! Expecting user data in the following format: USER_EMAIL APPLIANCE_HOST NUMBER_OF_FILES FILE_SIZE_KB !!!"
    exit 1
}

function acceptInvitations {
    # Accept invitations
    echo Accepting invitations...
    for i in `aerofs/aerofs-sh invitations | grep "@$emailDomain" | cut -d " " -f1`; do
        echo "aerofs/aerofs-sh accept $i"
        aerofs/aerofs-sh accept $i
    done
}


# exec > $logFile 2>&1
exec > >(tee -a $logFile)
# exec 2>&1

echo Running Aerofs Client Initialization Script
if [ -d "$DIRECTORY" ]; then
    echo `date`: AeroFS already installed. Starting AeroFS...
    nohup aerofs/aerofs-cli 0<&- &>> $logFile &
    sleep 10
    acceptInvitations
    echo DONE!!!
else
    # read user data
    user_data=`GET http://169.254.169.254/latest/user-data/`
    if [[ -z "$user_data" ]] ; then
        echo "User-data is empty!"
        displayUsageAndExit
    fi

    arr=($user_data)
    userPrefix=${arr[0]}
    password="uiuiui"
    if [[ -z "$userPrefix" ]] ; then
        echo "User is empty!"
        displayUsageAndExit
    fi
    appliance=${arr[1]}
    if [[ -z "$appliance" ]] ; then
        echo "Appliance host is empty!"
        displayUsageAndExit
    fi
    fileCount=${arr[2]}
    if [[ -z "$fileCount" ]] ; then
        fileCount=20 # default
    fi
    fileSizeKb=${arr[3]}
    if [[ -z "$fileSizeKb" ]] ; then
        fileSize=1048576 # default
    else
        fileSize=$((fileSizeKb*1024))
    fi
    instanceOrdinal=${arr[4]}
    if [[ -z "$instanceOrdinal" ]] ; then
        echo "Instance Ordinal parameter is empty!"
        displayUsageAndExit
    fi
    user="$userPrefix$instanceOrdinal@$emailDomain"
    totalInstances=${arr[5]}
    if [[ -z "$totalInstances" ]] ; then
        echo "Total instances count parameter is empty!"
        displayUsageAndExit
    fi
    echo *** User Data ***
    echo User: $user
    echo Password: DEFAULT
    echo Appliance: $appliance
    echo Files To Generate: $fileCount
    echo Generated File Size: $fileSize
    echo Instance Ordinal: $instanceOrdinal
    echo Total Instances: $totalInstances

    # create user
    echo Creating User
    ./signup.sh -w $appliance -d $appliance -u $user
    OUT=$?
    if [ $OUT -eq 0 ];then
       echo "Success!"
    else
       echo "Error when trying to create user. Error code: $OUT"
       exit $OUT
    fi

    # download client
    echo Downloading Client
    wget https://$appliance/static/installers/aerofs-installer.tgz --no-check-certificate

    if [ ! -f aerofs-installer.tgz ]; then
        echo "Can not download client installation from appliance: " $appliance
        exit 1
    fi

    # unpack client
    echo Unpacking Client
    tar -xvf aerofs-installer.tgz

    # create unattended setup file
    echo Generating Unattended Setup File
    mkdir .aerofs
    echo userid=$user > .aerofs/unattended-setup.properties
    echo password=$password >> .aerofs/unattended-setup.properties

#    echo "Starting AeroFS..."
    echo `date`: Starting AeroFS first time...
    nohup aerofs/aerofs-cli 0<&- &>> $logFile &

    # Wait until daemon starts
    tail -f $logFile | while read LOGLINE
    do
#        echo $LOGLINE
        if [[ ${LOGLINE} == *"$installedMsg"* ]]; then
            pkill -P $$ tail
        fi
    done
    echo `date`: AeroFS Started !


    # Cleanup shared folders if any
    echo Leaving existing shared folders...
    OIFS=$IFS;
    IFS=$'\n';
    for i in `aerofs/aerofs-sh shared`; do
        echo "aerofs/aerofs-sh leave $i"
        aerofs/aerofs-sh leave $i
    done
    IFS=$OIFS;

    # Create folders, generate files, send invitations
    echo Create folders, generate files, send invitations...
    for (( i=$instanceOrdinal+1; i<=$totalInstances; i++ ))
    do
        folderName="SHARE_$instanceOrdinal-$i"
        echo Shared folder: $folderName
        generateDummy $folderName
        echo "aerofs/aerofs-sh invite $folderName $userPrefix$i@$emailDomain"
        aerofs/aerofs-sh invite $folderName $userPrefix$i@$emailDomain
    done

    # waiting a little bit before accept invitations
    echo "Waiting $invitationsWaitTime seconds before accepting invitations"
    sleep $invitationsWaitTime

    acceptInvitations
    echo DONE!!!

fi