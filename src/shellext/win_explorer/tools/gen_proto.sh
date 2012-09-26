#!/bin/sh

GEN_ROOT=../gen
SRC_ROOT=../src
GUI_PROTO=../../../../src/gui/src/proto
LIB_PROTO=../../../../src/lib/src/proto

mkdir -p $GEN_ROOT

protoc -I=$LIB_PROTO --cpp_out=$GEN_ROOT $LIB_PROTO/path_status.proto
protoc -I=$LIB_PROTO -I=$GUI_PROTO --cpp_out=$GEN_ROOT $GUI_PROTO/shellext.proto
