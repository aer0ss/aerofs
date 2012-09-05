#include "service_generator.h"

#include <string>
#include <sstream>
#include <iostream>

#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/printer.h>
#include <google/protobuf/compiler/java/java_helpers.h>

#include "common/common.h"

using namespace google::protobuf;
using namespace google::protobuf::compiler::java;

// Private helper methods
string methodSignature(const Descriptor* message, bool signatureForStub);
void generateReactorSwitchCase(const MethodDescriptor* method, io::Printer* printer);
string CamelCaseToCapitalizedUnderscores(const string& input);
string methodEnumName(const MethodDescriptor* method);
string serviceInterfaceName(const ServiceDescriptor* service, bool fullyQualified);

/**
  Generate the public Service interface
*/
void ServiceGenerator::generateService(const ServiceDescriptor* service, io::Printer* printer)
{
    checkThatRpcErrorIsDefined(service);

    map<string, string> v1;
    v1["ServiceName"] = serviceInterfaceName(service, false);
    printer->Print(v1,
                "\n"
                "public interface $ServiceName$\n"
                "{\n");

    printer->Indent();

    // Generate the encodeError method
    map<string, string> v2;
    v2["reply"] = ClassName(service->method(0)->output_type());
    printer->Print(v2, "$reply$ encodeError(Throwable error);\n");

    // Generate the other methods
    for (int i = 1; i < service->method_count(); i++) {
        const MethodDescriptor* method = service->method(i);

        map<string, string> vars;
        vars["methodName"] = UnderscoresToCamelCase(method);
        vars["signature"] = methodSignature(method->input_type(), false);
        vars["reply"] = ClassName(method->output_type());

        printer->Print(vars, "public com.google.common.util.concurrent.ListenableFuture<$reply$> $methodName$($signature$) throws Exception;\n");
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
        enumRpcTypes << methodEnumName(service->method(i));
    }

    map<string, string> vars;
    vars["ServiceName"] = service->name();
    vars["ServiceInterface"] = serviceInterfaceName(service, true);
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
    printer->Indent();

    // Output the body of the switch statement
    // Start at 1 because the first method is the __error__ method
    for (int i = 1; i < service->method_count(); i++) {
        generateReactorSwitchCase(service->method(i), printer);
    }

    printer->Outdent();
    printer->Outdent();
    printer->Outdent();
    printer->Outdent();

    // Output the second half of the ServiceReactor
    #include "ServiceReactorPart2.tpl.h"
    printer->Print(vars, (char*) ServiceReactorPart2_tpl);
}

void ServiceGenerator::generateStub(const ServiceDescriptor* service, io::Printer* printer)
{
    map<string, string> vars;
    vars["ServiceName"] = service->name();
    vars["ServiceClassName"] = ClassName(service);
    vars["ErrorReplyClass"] = ClassName(service->method(0)->output_type());
    vars["MessageType"] = (service->file()->options().optimize_for() == FileOptions::LITE_RUNTIME)
            ? "com.google.protobuf.MessageLite"
            : "com.google.protobuf.Message";

    #include "ServiceStub.tpl.h"
    printer->Print(vars, (char*) ServiceStub_tpl);

    printer->Indent();

    // Start at 1 because the first method is the __error__ method
    for (int i = 1; i < service->method_count(); i++) {

        const MethodDescriptor* method = service->method(i);
        map<string, string> subvars;
        subvars["methodName"] = UnderscoresToCamelCase(method);
        subvars["signature"] = methodSignature(method->input_type(), true);
        subvars["CallClass"] = ClassName(method->input_type());
        subvars["ReplyClass"] = ClassName(method->output_type());
        subvars["RPC_TYPE"] = methodEnumName(method);

        subvars.insert(vars.begin(), vars.end());

        printer->Print(subvars,
                       "\n"
                       "public com.google.common.util.concurrent.ListenableFuture<$ReplyClass$> $methodName$($signature$)\n"
                       "{\n"
                       "  $CallClass$.Builder builder = $CallClass$.newBuilder();\n");

       for (int j = 0; j < method->input_type()->field_count(); j++) {
           const FieldDescriptor* field = method->input_type()->field(j);
           map<string, string> vars;
           vars["Field"] = UnderscoresToCapitalizedCamelCase(field);
           vars["varName"] = UnderscoresToCamelCase(field);

           if (field->is_optional()) {
               printer->Print(vars,
                           "  if ($varName$ != null) { builder.set$Field$($varName$); }\n");
           } else if (field->is_repeated()) {
               printer->Print(vars,
                           "  if ($varName$ != null) { builder.addAll$Field$($varName$); }\n");
           } else {
               printer->Print(vars,
                           "  builder.set$Field$($varName$);\n");
           }
       }

       printer->Print(subvars,
                       "\n"
                       "  return sendQuery($ServiceClassName$Reactor.ServiceRpcTypes.$RPC_TYPE$, builder.build().toByteString(), $ReplyClass$.newBuilder());\n"
                      "}\n"
                       );
    }

    printer->Outdent();
    printer->Print("}\n");
}

void ServiceGenerator::generateBlockingStub(const ServiceDescriptor* service, io::Printer* printer)
{
    map<string, string> vars;
    vars["ServiceName"] = service->name();

    #include "BlockingStub.tpl.h"
    printer->Print(vars, (char*) BlockingStub_tpl);

    printer->Indent();

    // Start at 1 because the first method is the __error__ method
    for (int i = 1; i < service->method_count(); i++) {

        const MethodDescriptor* method = service->method(i);

        // generate a string with all the arguments separated by comma
        stringstream args;
        for (int i = 0; i < method->input_type()->field_count(); i++) {
            if (i > 0) {
                args << ", ";
            }
            args << method->input_type()->field(i)->camelcase_name();
        }

        map<string, string> vars;
        vars["ServiceName"] = service->name();
        vars["methodName"] = UnderscoresToCamelCase(method);
        vars["signature"] = methodSignature(method->input_type(), true);
        vars["ReplyClass"] = ClassName(method->output_type());
        vars["args"] = args.str();

        printer->Print(vars,
                       "\n"
                       "public $ReplyClass$ $methodName$($signature$) throws Exception\n"
                       "{\n"
                       "  try {\n"
                       "    return com.google.common.util.concurrent.Futures.get(_stub.$methodName$($args$), Exception.class);\n"
                       "  } catch (Exception e) {\n"
                       "    if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}\n"
                       "    else {throw e;}\n"
                       "  }\n"
                       "}\n"
                       );
    }

    printer->Outdent();
    printer->Print("}\n");
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
string methodSignature(const Descriptor* message, bool signatureForStub)
{
    stringstream signature;

    for (int i = 0; i < message->field_count(); i++) {
        const FieldDescriptor* field = message->field(i);

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

        if (field->is_repeated()) {
            if (signatureForStub) {
                javaType = "java.lang.Iterable<" + javaType + ">";
            } else {
                javaType = "java.util.List<" + javaType + ">";
            }
        }

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
            methodParams << ",\n                               ";
        }
        const FieldDescriptor* field = method->input_type()->field(i);
        const string list = field->is_repeated() ? "List" : "";
        string getter = "call.get" + UnderscoresToCapitalizedCamelCase(field) + list + "()";

        // Pass null if an optional field is not set
        if (field->is_optional()) {
            getter = "call.has" + UnderscoresToCapitalizedCamelCase(field) + "() ? " + getter + " : null";
        }
        methodParams << getter;
    }

    map<string, string> vars;
    vars["LABEL_NAME"] = methodEnumName(method);
    vars["CallClassName"] = ClassName(method->input_type());
    vars["methodName"] = UnderscoresToCamelCase(method);
    vars["methodParams"] = methodParams.str();

    printer->Print(vars,
                   "case $LABEL_NAME$: {\n");
    if (method->input_type()->field_count() > 0) {
        printer->Print(vars,
                   "  $CallClassName$ call = $CallClassName$.parseFrom(p.getPayloadData());\n");
    }
    printer->Print(vars,
                   "  reply = _service.$methodName$($methodParams$);\n"
                   "  break;\n"
                   "}\n");
}

string methodEnumName(const MethodDescriptor* method)
{
    return CamelCaseToCapitalizedUnderscores(method->name());
}

/**
  Return the interface name for the service
  @param fullyQualified: whether we should return the full Java name or just the interface name
 */
string serviceInterfaceName(const ServiceDescriptor* service, bool fullyQualified)
{
    string name = "I" + service->name();
    if (fullyQualified) {
        const FileDescriptor* file = service->file();
        string result;
        if (file->options().java_multiple_files()) {
            result = FileJavaPackage(file);
        } else {
            result = ClassName(file);
        }
        if (!result.empty()) {
            result += '.';
        }
        result += name;
        name = result;
    }
    return name;
}
