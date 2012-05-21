include( ../common/protoc-plugin.pri )

TARGET = protoc-gen-rpc-objc

SOURCES += \
    ../3rd_party/google/protobuf/compiler/objc/objc_helpers.cc \   # FIXME: These come from protobuf-objc
    ../3rd_party/google/protobuf/objectivec-descriptor.pb.cc \     # We should probably add it as a dependendency
    gen_rpc_objc.cc \
    service_generator.cc \
    ../common/common.cc

HEADERS += \
    gen_rpc_objc.h \
    service_generator.h

TEMPLATES += \
    ServiceStubHeader.tpl \
    ServiceStubImplPart1.tpl \
    ServiceStubImplPart2.tpl

OTHER_FILES += $$TEMPLATES
