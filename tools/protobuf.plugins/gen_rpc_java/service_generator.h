#ifndef SERVICE_GENERATOR_H
#define SERVICE_GENERATOR_H

namespace google {
    namespace protobuf {
        class ServiceDescriptor;     // descriptor.h
        namespace io {
            class Printer;           // printer.h
        }
    }
}

class ServiceGenerator
{
public:
    static void generateService(const google::protobuf::ServiceDescriptor* service, google::protobuf::io::Printer* printer);
    static void generateReactor(const google::protobuf::ServiceDescriptor* service, google::protobuf::io::Printer* printer);
};

#endif // SERVICE_GENERATOR_H
