GEN_ROOT=gen
SRC_ROOT=src
PROTOC=protoc
PROTOC_OBJC_PLUGIN=protoc-gen-objc
GUI_PROTO=../../gui/src/proto
LIB_PROTO=../../lib/src/proto

ifeq (${shell which ${PROTOC_OBJC_PLUGIN}},)
objc_plugin_found = no
else
objc_plugin_found = yes
endif

all: ${GEN_ROOT} \
	gen_proto

${GEN_ROOT}:
	mkdir -p ${GEN_ROOT}

gen_proto:
	@echo \\nGenerating Objective-C protobuf classes...\\n;
ifeq (${objc_plugin_found}, yes)
	${PROTOC} -I=${LIB_PROTO} --objc_out=${GEN_ROOT} ${LIB_PROTO}/path_status.proto
	${PROTOC} -I=${LIB_PROTO} -I=${GUI_PROTO} --objc_out=${GEN_ROOT} ${GUI_PROTO}/shellext.proto
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
