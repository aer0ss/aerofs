include( ../common/protoc-plugin.pri )

TARGET = protoc-gen-rpc-python

SOURCES += \
    gen_rpc_python.cc \
    service_generator.cc \
    util.cc

HEADERS += \
    gen_rpc_objc.h \
    service_generator.h \
    util.h

TEMPLATES += \
    ServiceStubHeader.tpl \
    RpcMethodStub.tpl

OTHER_FILES += $$TEMPLATES
