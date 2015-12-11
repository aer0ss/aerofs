#!/bin/bash

set -e

# Hey, databases! Let's start this one for tests. Annoying, right?
service mysql start

for d in $(ls "/gopath/src/$1") ; do
    GO15VENDOREXPERIMENT=1 go get -t "$1/$d"
    GO15VENDOREXPERIMENT=1 go test -v "$1/$d"
done

