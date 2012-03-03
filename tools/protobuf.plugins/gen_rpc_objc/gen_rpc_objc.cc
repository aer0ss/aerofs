#include "gen_rpc_objc.h"

#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/io/zero_copy_stream.h>
#include <google/protobuf/io/printer.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/compiler/objc/objc_helpers.h>

#include "service_generator.h"

using namespace google::protobuf;

int main(int argc, char* argv[])
{
    GenRpcObjc generator;
    return compiler::PluginMain(argc, argv, &generator);
}

bool GenRpcObjc::Generate(const FileDescriptor* file, const std::string& /*parameter*/, compiler::OutputDirectory* output_directory, std::string* /*error*/) const
{
    string basename = compiler::objectivec::FilePath(file);

    GOOGLE_LOG(FATAL) << "GENERATE ERROR HANDLING CODE";

    // Generate header.
    {
        internal::scoped_ptr<io::ZeroCopyOutputStream> output(output_directory->OpenForInsert(basename + ".pb.h", "global_scope"));
        io::Printer printer(output.get(), '$');

        for (int i = 0; i < file->service_count(); i++) {
            ServiceGenerator::generateStubHeader(file->service(i), &printer);
        }
    }

    // Generate cc file.
    {
        internal::scoped_ptr<io::ZeroCopyOutputStream> output(output_directory->OpenForInsert(basename + ".pb.m", "global_scope"));
        io::Printer printer(output.get(), '$');

        for (int i = 0; i < file->service_count(); i++) {
            ServiceGenerator::generateStubImpl(file->service(i), &printer);
        }
    }
    return true;
}
