include( ../common/protoc-plugin.pri )

TARGET = protoc-gen-rpc-java

SOURCES += \
    gen_rpc_java.cc \
    service_generator.cc

HEADERS += \
    gen_rpc_java.h \
    service_generator.h

TEMPLATES += \
    ServiceReactorPart1.tpl \
    ServiceReactorPart2.tpl

OTHER_FILES += $$TEMPLATES
