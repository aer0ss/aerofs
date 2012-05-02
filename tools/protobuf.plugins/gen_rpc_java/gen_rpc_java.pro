include( ../common/protoc-plugin.pri )

TARGET = protoc-gen-rpc-java

SOURCES += \
    gen_rpc_java.cc \
    service_generator.cc \
    ../common/common.cc

HEADERS += \
    gen_rpc_java.h \
    service_generator.h \
    ../common/common.h

TEMPLATES += \
    ServiceReactorPart1.tpl \
    ServiceReactorPart2.tpl \
    ServiceStub.tpl \
    BlockingStub.tpl

OTHER_FILES += $$TEMPLATES
