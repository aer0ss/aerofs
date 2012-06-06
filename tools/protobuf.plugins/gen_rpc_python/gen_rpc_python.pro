include( ../common/protoc-plugin.pri )

TARGET = protoc-gen-rpc-python

SOURCES += \
    gen_rpc_python.cc \
    service_generator.cc \
    util.cc \
    ../common/common.cc

HEADERS += \
    gen_rpc_objc.h \
    service_generator.h \
    util.h \
    ../common/common.h

TEMPLATES += \
    ServiceStubHeader.tpl \
    RpcMethodStub.tpl \
    ServiceReactorPart1.tpl \
    ServiceReactorPart2.tpl

OTHER_FILES += $$TEMPLATES
