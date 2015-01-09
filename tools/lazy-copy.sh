#!/bin/bash
#
# This script copies files recursively from $1 to $2. A file is copied only if it doesn't
# exist in the target or its content differs at the source and target. If the file is a
# gzip or gzipped tar archive, their data are decomparessed before comparison. This is to
# exclude file diferences caused by timestamp changes.
#
# Why lazy copying? It's important to retain Docker's image caches to reduce 1) download
# sizes for image upgrades and 2) time spent in incremental builds. However, file changes
# bust image caches. Hence we try best to minimize file content as well as metadata
# changes in the buildroot folder.  Using make or similar tools is not sufficient as they
# do not compare file content.

is_gzip() {
    [[ $(file -bI "$1") =~ application/x-gzip ]] && echo 1 || echo 0
}

is_tar() {
    [[ $(file -bI "$1") =~ application/x-tar ]] && echo 1 || echo 0
}

NO_DIFF=0
DIFF=1

function deep_diff() {
    trap cleanup_deep_diff EXIT

    if [ $(is_gzip "$1") = 0 ]; then
        [[ $(diff $1 $2) ]] && return ${DIFF} || return ${NO_DIFF}
    fi

    # $1 is a gzip
    if [ $(is_gzip "$2") = 0 ]; then
        return ${DIFF}
    fi

    echo "deep-diff: gunzipping $1"
    # Both $1 and $2 are gzip. Decompress to a temp folder
    UNZIP1=$(mktemp -t deep-diff-unzip)
    gunzip -c "$1" > ${UNZIP1}
    UNZIP2=$(mktemp -t deep-diff-unzip)
    gunzip -c "$2" > ${UNZIP2}

    if [ $(is_tar ${UNZIP1}) = 0 ]; then
        [[ $(diff ${UNZIP1} ${UNZIP2}) ]] && return ${DIFF} || return ${NO_DIFF}
    fi

    # $1 is a gzipped tar
    if [ $(is_tar ${UNZIP2}) = 0 ]; then
        return ${DIFF}
    fi

    echo "deep-diff: untarring $1"
    UNTAR1=$(mktemp -dt deep-diff-untar)
    tar xf ${UNZIP1} -C ${UNTAR1}
    UNTAR2=$(mktemp -dt deep-diff-untar)
    tar xf ${UNZIP2} -C ${UNTAR2}
    [[ $(diff -r ${UNTAR1} ${UNTAR2}) ]] && return ${DIFF} || return ${NO_DIFF}
}

function cleanup_deep_diff() {
    if [ -n "${UNZIP1}" ]; then rm -f ${UNZIP1}; fi
    if [ -n "${UNZIP2}" ]; then rm -f ${UNZIP2}; fi
    if [ -n "${UNTAR1}" ]; then rm -rf ${UNTAR1}; fi
    if [ -n "${UNTAR2}" ]; then rm -rf ${UNTAR2}; fi
}

function copy_file() {
    echo "cp $1 $2"
    cp "$1" "$2"
}


if [ -d "$1" ]; then
    # recurse on directories
    mkdir -p "$2"
    for i in $(ls "$1"); do
        "$0" "$1/$i" "$2/$i"
    done

elif [ ! -f "$1" ]; then
    echo "$1 is not a dir or a regular file."

elif [ ! -f "$2" ]; then
    # target file doesn't exist
    copy_file "$1" "$2"

elif [ "$1" -nt "$2" ]; then
    # target exists and older than the source. do a deep diff
    deep_diff "$1" "$2"
    if [ $? = ${NO_DIFF} ]; then
        echo "content unchanged: $1"
    else
        copy_file "$1" "$2"
    fi
fi

