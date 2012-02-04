MODULE_DIR=tap

PROTOC=protoc
RPC_PLUGIN=../../tools/protobuf.plugins/bin/protoc-gen-rpc-python
RPC_PROTO=../../tools/protobuf.plugins/proto
SRC_LIB_PROTO=../../java/aerofs.lib/src/proto
SRC_DAEMON_PROTO=../../java/aerofs.daemon/src/proto
GEN_PROTO=${MODULE_DIR}

PROTO_OBJECTS=${SRC_DAEMON_PROTO}/tap.rpc \
			  ${SRC_LIB_PROTO}/common.gen \
			  ${SRC_DAEMON_PROTO}/transport.gen

all: rpc_proto \
	 ${PROTO_OBJECTS}

rpc_proto:
	${PROTOC} -I=${RPC_PROTO} --python_out=${GEN_PROTO} ${RPC_PROTO}/rpc_service.proto

%.gen: 
	${PROTOC} -I=${SRC_LIB_PROTO} -I=${SRC_DAEMON_PROTO} --python_out=${GEN_PROTO} $*.proto

%.rpc:
	${PROTOC} -I=${SRC_LIB_PROTO} -I=${SRC_DAEMON_PROTO} --plugin=${RPC_PLUGIN} --python_out=${GEN_PROTO} --rpc-python_out=${GEN_PROTO} $*.proto

clean:
	rm -f ${GEN_PROTO}/*_pb2.py ${GEN_PROTO}/*.pyc *.pyc