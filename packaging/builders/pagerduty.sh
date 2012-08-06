#!/bin/bash -e

mkdir -p pagerduty/usr/bin

for script in kssh kscp
do
    cp ../tools/$script pagerduty/usr/bin
done
