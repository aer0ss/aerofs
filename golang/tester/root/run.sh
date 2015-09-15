#!/bin/bash

set -e

# Hey, databases! Let's start this one for tests. Annoying, right?
service mysql start

for d in $(ls "/gopath/src/$1") ; do
    go get -t "$1/$d"
    go test -v "$1/$d"
done

