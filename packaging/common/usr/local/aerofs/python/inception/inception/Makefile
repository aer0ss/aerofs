
.PHONY: all clean

REPO_ROOT=../../../../../../../..

RPC_PLUGIN=${REPO_ROOT}/tools/protobuf.plugins/bin/protoc-gen-rpc-python
RPC_PROTO=${REPO_ROOT}/tools/protobuf.plugins/proto

SRC_LIB_PROTO=proto
GEN_DIR=gen

all: \
	${GEN_DIR} \
	${RPC_PROTO}/rpc_service.proto \
	kvm.gen_proto \
	vmhost.gen_proto \
	admin.gen_proto \
	gen_rpc_proto

${GEN_DIR}:
	mkdir -p ${GEN_DIR}
	touch ${GEN_DIR}/__init__.py

gen_rpc_proto:
	protoc \
		-I=${RPC_PROTO} \
		--python_out=${GEN_DIR} ${RPC_PROTO}/rpc_service.proto

%.gen_proto:
	protoc \
		-I=${SRC_LIB_PROTO} \
		--plugin=${RPC_PLUGIN} \
		--python_out=${GEN_DIR} \
		--rpc-python_out=${GEN_DIR} ${SRC_LIB_PROTO}/$*.proto

clean:
	@rm -rf ${GEN_DIR}
