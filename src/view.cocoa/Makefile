GEN_ROOT=gen
PROTOC=protoc
PROTOC_OBJC_PLUGIN=protoc-gen-objc
RPC_PLUGIN=../../tools/protobuf.plugins/bin/protoc-gen-rpc-objc
RPC_PROTO=../../tools/protobuf.plugins/proto
GUI_PROTO=../gui/src/proto
LIB_PROTO=../lib/src/proto

ifeq (${shell which ${PROTOC_OBJC_PLUGIN}},)
objc_plugin_found = no
else
objc_plugin_found = yes
endif

all: ${GEN_ROOT} \
	gen_rpc_service \
	controller.gen

${GEN_ROOT}:
	mkdir -p ${GEN_ROOT}

gen_rpc_service:
	${PROTOC} -I=${LIB_PROTO} --objc_out=${GEN_ROOT} ${LIB_PROTO}/common.proto
	${PROTOC} -I=${GUI_PROTO} --objc_out=${GEN_ROOT} ${GUI_PROTO}/controller_notifications.proto
	${PROTOC} -I=${RPC_PROTO} --objc_out=${GEN_ROOT} ${RPC_PROTO}/rpc_service.proto

%.gen:
	@echo \\nGenerating Objective-C protobuf classes...\\n;
ifeq (${objc_plugin_found}, yes)

	${PROTOC} --plugin=${RPC_PLUGIN} -I=${GUI_PROTO} -I=${LIB_PROTO} --objc_out=${GEN_ROOT} --rpc-objc_out=${GEN_ROOT} ${GUI_PROTO}/$*.proto
	@echo \\nDone;
else
	@echo NOTE:                                                  \\n\
	  You don\'t have the Protobuf Objective-C plugin installed. \\n\
	  You won\'t be able to compile AeroFS\'s Finder Extension.  \\n\
	                                                             \\n\
	  To install the Protobuf Objective-C plugin:                \\n\
	  $$ git clone https://github.com/aerofs/protobuf-objc       \\n\
	  $$ cd protobuf-objc                                        \\n\
	  $$ ./autogen.sh                                            \\n\
	  $$ ./configure                                             \\n\
	  $$ make \&\& make install                                  \\n\
	;
endif

clean:
	rm -rf ${GEN_ROOT}

