#include "gen_rpc_python.h"

#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/io/zero_copy_stream.h>
#include <google/protobuf/io/printer.h>
#include <google/protobuf/descriptor.h>

#include "service_generator.h"
#include "util.h"

using namespace google::protobuf;

int main(int argc, char* argv[])
{
    GenRpcPython generator;
    return compiler::PluginMain(argc, argv, &generator);
}

bool GenRpcPython::Generate(const FileDescriptor* file, const std::string& /*parameter*/, compiler::OutputDirectory* output_directory, std::string* /*error*/) const
{
    const string& basename = stripExtension(getBaseFilename(file->name()));

    // Add import statements
    {
        internal::scoped_ptr<io::ZeroCopyOutputStream> output(output_directory->OpenForInsert(basename + "_pb2.py", "imports"));
        io::Printer printer(output.get(), '$');

        // add the necessary import
        printer.Print("import rpc_service_pb2\n");

        /*for (int i = 0; i < file->service_count(); i++) {
            ServiceGenerator::generateStubImpl(file->service(i), &printer);
        }*/
    }

    // Generate py file.
    {
        internal::scoped_ptr<io::ZeroCopyOutputStream> output(output_directory->OpenForInsert(basename + "_pb2.py", "module_scope"));
        io::Printer printer(output.get(), '$');

        for (int i = 0; i < file->service_count(); i++) {
            ServiceGenerator::generateStubImpl(file->service(i), &printer);
        }
    }
    return true;
}
