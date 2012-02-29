#ifndef PLUGIN_H
#define PLUGIN_H

#include <google/protobuf/compiler/code_generator.h>

class GenRpcJava : public google::protobuf::compiler::CodeGenerator
{
public:
    GenRpcJava() {}
    virtual ~GenRpcJava() {}
    virtual bool Generate(const google::protobuf::FileDescriptor* file, const std::string& parameter, google::protobuf::compiler::OutputDirectory* output_directory, std::string* error) const;
};

#endif // PLUGIN_H
