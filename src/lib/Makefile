all: gen_rpc_service \
	common.gen_proto \
	files.gen_proto \
	path_status.gen_proto \
	mobile.gen_proto \
	ritual_notifications.gen_proto \
	ritual.gen_proto \
	sp.gen_proto \
	sv.gen_proto \
	sync_status.gen_proto \

include ../lib/Makefile.common

gen_rpc_service: ${GEN_ROOT}
	${PROTOC} -I=${RPC_PROTO} --java_out=${GEN_ROOT} ${RPC_PROTO}/rpc_service.proto
