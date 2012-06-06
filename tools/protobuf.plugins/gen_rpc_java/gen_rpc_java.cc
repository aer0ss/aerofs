#include "gen_rpc_java.h"

#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/compiler/java/java_helpers.h>
#include <google/protobuf/io/zero_copy_stream.h>
#include <google/protobuf/io/printer.h>

#include "service_generator.h"

using namespace google::protobuf;
using namespace google::protobuf::compiler::java;

int main(int argc, char* argv[])
{
    GenRpcJava generator;
    return compiler::PluginMain(argc, argv, &generator);
}

bool GenRpcJava::Generate(const FileDescriptor* file, const std::string& /*parameter*/, compiler::OutputDirectory* output_directory, std::string* error) const
{
    if (file->service_count() > 0 && file->options().java_generic_services()) {
        *error = "You must set \"option java_generic_services = false;\" to use the service generater plugin.";
        return false;
    }

    if (file->options().java_multiple_files()) {
        // See captnproto source code for an example of how to deal with this option
        *error = "gen_rpc_java does not support the java_multiple_files option yet.";
        return false;
    }

    string package_dir = JavaPackageToDir(FileJavaPackage(file));
    string java_filename = package_dir;
    java_filename += FileClassName(file);
    java_filename += ".java";

    scoped_ptr<io::ZeroCopyOutputStream> output(output_directory->OpenForInsert(java_filename, "outer_class_scope"));
    io::Printer printer(output.get(), '$');

    for (int i = 0; i < file->service_count(); i++) {
        ServiceGenerator::generateService(file->service(i), &printer);
        ServiceGenerator::generateReactor(file->service(i), &printer);
        ServiceGenerator::generateStub(file->service(i), &printer);
        ServiceGenerator::generateBlockingStub(file->service(i), &printer);
    }

    return true;
}
