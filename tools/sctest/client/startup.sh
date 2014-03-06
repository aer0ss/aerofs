#!/bin/bash

function generateDummy {
    mkdir -p AeroFS
    for  ((x=0; x<$fileCount; ++x))
    do
        uuid=`uuidgen`
        head -c $fileSize /dev/urandom > AeroFS/dummy$x"_"$uuid
    done;
}

function displayUsageAndExit {
    echo "!!! Expecting user data in the following format: USER_EMAIL APPLIANCE_HOST NUMBER_OF_FILES FILE_SIZE_KB !!!"
    exit 1
}


DIRECTORY='.aerofs'
echo Running Aerofs Client Initialization Script
if [ -d "$DIRECTORY" ]; then
    echo "AeroFS already installed. Starting AeroFS..."
    echo `date`: Starting AeroFS... >> aerofs.log
    nohup aerofs/aerofs-cli 0<&- &>> aerofs.log &
else
    # read user data
    user_data=`GET http://169.254.169.254/latest/user-data/`
    if [[ -z "$user_data" ]] ; then
        echo "User-data is empty!"
        displayUsageAndExit
    fi

    arr=($user_data)
    user=${arr[0]}
    password="uiuiui"
    if [[ -z "$user" ]] ; then
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
    echo *** User Data ***
    echo User: $user
    echo Password: DEFAULT
    echo Appliance: $appliance
    echo Files To Generate: $fileCount
    echo Generated File Size: $fileSize

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

    echo "Generating dummy files"
    generateDummy

    echo "Starting AeroFS..."
    echo `date`: Starting AeroFS first time... > aerofs.log
    nohup aerofs/aerofs-cli 0<&- &>> aerofs.log &
fi