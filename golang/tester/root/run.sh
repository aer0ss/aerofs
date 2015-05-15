#!/bin/bash

set -e

for d in $(ls "/gopath/src/$1") ; do
    go get -t "$1/$d"
    go test -v "$1/$d"
done

