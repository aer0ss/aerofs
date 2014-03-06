#!/bin/bash

instancePrefix="sergey-test-client" # unique prefix for client instancess
applianceHost="ec2-54-236-219-57.compute-1.amazonaws.com" # AeroFS appliance ec2 public dns name
fileSizeKb=100 # file size for each randomly generated file

subnet=subnet-3927244d # subnet id for all your clients
image=ami-03919c6a # client image id
instanceType=t1.micro # ec2 instance type
userEmailName="sergey+sctest" # common prefix for all generated users
userEmailDomain="aerofs.com" # common domain for all generated users

die () {
    echo >&2 "$@"
    exit 1
}

[ "$#" -eq 2 ] || die "2 arguments required (fileCount, userCount), $# provided"
echo $1 | grep -E -q '^[0-9]+$' || die "Numeric argument required, $1 provided"
echo $2 | grep -E -q '^[0-9]+$' || die "Numeric argument required, $2 provided"
# echo $3 | grep -E -q '^[0-9]+$' || die "Numeric argument required, $3 provided"

# instancesPerUser=$1
instancesPerUser=1
numberOfFiles=$1
userCount=$2
firstUserIndex=1

echo $'\n'"Terminating Previous Instances..."$'\n'
./terminateClients.sh
echo $'\n'"Instances have been terminated. Waiting 60 seconds..."
sleep 60

echo $'\n'"Starting Instances..."$'\n'
lastUserIndex=$(($userCount+$firstUserIndex-1))
for (( i=$firstUserIndex; i<=$lastUserIndex; i++ ))
do
    email="$userEmailName$i@$userEmailDomain"
    userData="$userEmailName $applianceHost $numberOfFiles $fileSizeKb $i $lastUserIndex"
    echo "*** $i] UserData=$userData ***"
    for (( j=1; j<=$instancesPerUser; j++ ))
    do
        echo "$j) ec2-run-instances $image -n 1 -k sergeys -t $instanceType --subnet $subnet --associate-public-ip-address true -d \"$userData\""$'\n'
        start=`ec2-run-instances $image -n 1 -k sergeys -t $instanceType --subnet $subnet --associate-public-ip-address true -d \""$userData\""`
        echo $start
        INSTANCE=$(echo "$start" | awk '/^INSTANCE/ {print $2}')
            if [ $? != 0 ]; then
                echo "FAILURE: Machine did not start" 1>&2
                exit 1
            fi
        if [[ -z $INSTANCE ]]; then
            die "FAILURE: Instanse ID is empty; Machine most likely did not start"
        fi

        tagCommand="ec2-create-tags $INSTANCE --tag \"Name=$instancePrefix$i-$j\""
        echo
        echo "$tagCommand"$'\n'
        tag=$($tagCommand)
        echo $tag
            if [ $? != 0 ]; then
                die "FAILURE: Can not tag instance"
            fi
        echo
    done
    echo "****************"
    echo
    echo
done
echo "*** SUCCESS ***"
echo "Total instances created: $((userCount*instancesPerUser))"