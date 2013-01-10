#!/bin/sh

GEN_ROOT=../gen
SRC_ROOT=../src
GUI_PROTO=../../../../src/gui/src/proto
BASE_PROTO=../../../../src/base/src/proto
LIB_PROTO=../../../../src/lib/src/proto
PROTOC=../3rdparty/bin/protoc.exe

mkdir -p $GEN_ROOT

$PROTOC -I=$BASE_PROTO -I=$LIB_PROTO --cpp_out=$GEN_ROOT $LIB_PROTO/path_status.proto
$PROTOC -I=$BASE_PROTO -I=$LIB_PROTO -I=$GUI_PROTO --cpp_out=$GEN_ROOT $GUI_PROTO/shellext.proto
