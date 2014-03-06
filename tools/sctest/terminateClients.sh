#!/bin/bash
instancePrefix="sergey-test-client" # unique prefix for client instancess
count=1
for i in `ec2din --filter "tag:Name=$instancePrefix*" | grep INSTANCE | grep -v terminated | cut -f2`; do
    echo $count") ec2-terminate-instances "$i
    ec2-terminate-instances $i
    count=$((count+1))
done