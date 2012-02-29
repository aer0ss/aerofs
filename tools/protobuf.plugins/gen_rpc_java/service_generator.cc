#include "service_generator.h"

#include <string>
#include <sstream>

#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/printer.h>
#include <google/protobuf/compiler/java/java_helpers.h>

using namespace google::protobuf;
using namespace google::protobuf::compiler::java;

// Private helper methods
string methodSignature(const Descriptor* message);
void generateReactorSwitchCase(const MethodDescriptor* method, io::Printer* printer);
string CamelCaseToCapitalizedUnderscores(const string& input);

/**
  Generate the public Service interface
*/
void ServiceGenerator::generateService(const ServiceDescriptor* service, io::Printer* printer)
{
    printer->Print(
                "\n"
                "public interface $ServiceName$\n"
                "{\n",
                "ServiceName", service->name());

    printer->Indent();

    for (int i = 0; i < service->method_count(); i++) {
        const MethodDescriptor* method = service->method(i);

        map<string, string> vars;
        vars["methodName"] = UnderscoresToCamelCase(method);
        vars["signature"] = methodSignature(method->input_type());
        vars["reply"] = ClassName(method->output_type());

        printer->Print(vars, "public com.google.common.util.concurrent.ListenableFuture<$reply$> $methodName$($signature$);\n");
    }

    printer->Outdent();
    printer->Print("}\n");
}

/**
  Generates the ServiceReactor class
*/
void ServiceGenerator::generateReactor(const ServiceDescriptor* service, io::Printer* printer)
{
    // For each rpc method, create an enum value (ie: addPerson -> ADD_PERSON)
    stringstream enumRpcTypes;
    for (int i = 0; i < service->method_count(); i++) {
        if (i > 0) {
            enumRpcTypes << ",\n    ";
        }
        enumRpcTypes << CamelCaseToCapitalizedUnderscores(service->method(i)->name());
    }

    map<string, string> vars;
    vars["ServiceName"] = service->name();
    vars["ServiceClassName"] = ClassName(service);
    vars["EnumRpcTypes"] = enumRpcTypes.str();
    vars["BaseMessageClass"] = (service->file()->options().optimize_for() == FileOptions::LITE_RUNTIME)
            ? "com.google.protobuf.GeneratedMessageLite"
            : "com.google.protobuf.GeneratedMessage";

    // Output the first half of our ServiceReactor class
    #include "ServiceReactorPart1.tpl.h"
    printer->Print(vars, (char*) ServiceReactorPart1_tpl);

    printer->Indent();
    printer->Indent();
    printer->Indent();

    // Output the body of the switch statement
    for (int i = 0; i < service->method_count(); i++) {
        generateReactorSwitchCase(service->method(i), printer);
    }

    printer->Outdent();
    printer->Outdent();
    printer->Outdent();

    // Output the second half of the ServiceReactor
    #include "ServiceReactorPart2.tpl.h"
    printer->Print(vars, (char*) ServiceReactorPart2_tpl);
}

/**
  Helper method to get a signature string

  For example, given the following protobuf message:

  @code
    option java_outer_classname = "AB";
    message AddPersonCall {
        required Person person = 1;
        required int32 another_field = 2;
    }
  @endcode

  it returns: @codeline "AB.Person person, java.lang.Integer anotherField"
*/
string methodSignature(const Descriptor* message)
{
    stringstream signature;

    for (int i = 0; i < message->field_count(); i++) {
        const FieldDescriptor* field = message->field(i);

        if (!field->is_required()) {
            GOOGLE_LOG(FATAL) << "\n"
                              << "All RPC input message fields must be required.\n"
                              << "  field: " << field->name() << "\n"
                              << "  in message: " << message->name() << "\n"
                              << "  in file: " << message->file()->name() << "\n";
        }

        string javaType;
        JavaType type = GetJavaType(field);
        switch (type) {
        case JAVATYPE_MESSAGE:
            javaType = ClassName(field->message_type());
            break;
        case JAVATYPE_ENUM:
            javaType = ClassName(field->enum_type());
            break;
        default:
            javaType = string(BoxedPrimitiveTypeName(type));
        }

        GOOGLE_CHECK(!javaType.empty());

        if (i > 0) {
            signature << ", ";
        }
        signature << javaType << " " << field->camelcase_name();
    }
    return signature.str();
}

void generateReactorSwitchCase(const MethodDescriptor* method, io::Printer* printer)
{
    // Generate the string that will be passed as the parameter list for the method
    stringstream methodParams;
    for (int i = 0; i < method->input_type()->field_count(); i++) {
        if (i > 0) {
            methodParams << ", ";
        }
        const FieldDescriptor* field = method->input_type()->field(i);
        methodParams << "call.get" << UnderscoresToCapitalizedCamelCase(field) << "()";
    }

    map<string, string> vars;
    vars["LABEL_NAME"] = CamelCaseToCapitalizedUnderscores(method->name());
    vars["CallClassName"] = ClassName(method->input_type());
    vars["methodName"] = UnderscoresToCamelCase(method);
    vars["methodParams"] = methodParams.str();

    printer->Print(vars,
                   "case $LABEL_NAME$:\n");
    if (method->input_type()->field_count() > 0) {
        printer->Print(vars,
                   "  $CallClassName$ call = $CallClassName$.parseFrom(p.getPayloadData());\n");
    }
    printer->Print(vars,
                   "  reply = _service.$methodName$($methodParams$);\n"
                   "  break;\n"
                   );
}

/**
  Helper function to convert a camelCasedName to CAPITALIZED_UNDERSCORE_NAME
*/
string CamelCaseToCapitalizedUnderscores(const string& input)
{
    std::string result;
    for (unsigned int i = 0; i < input.size(); i++) {
        if ('a' <= input[i] && input[i] <= 'z') {
            result += input[i] + ('A' - 'a');
            // Insert an underscore iff this character is lowercase and next character is uppercase
            if (i < input.size() - 1 && 'A' <= input[i + 1] && input[i + 1] <= 'Z') {
                result += '_';
            }
        } else {
            result += input[i];
        }
    }
    return result;
}
