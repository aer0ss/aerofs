#include "service_generator.h"

#include <sstream>

#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/printer.h>

#include "util.h"
#include "common/common.h"

using namespace google::protobuf;

#define INDENT "  "

void generateMethodStub(const MethodDescriptor* service, io::Printer* printer, char* methodTemplate);
string generateMethodCallEnums(const ServiceDescriptor* service);
string methodNameToPythonEnum(const MethodDescriptor* name);
string pythonScopedMessageName(const FileDescriptor* thisFile, const Descriptor* message);
string messageFieldsToParameterList(const Descriptor* message);
string messageFieldAssignmentToString(const Descriptor* message);

/**
  Generates the Service abstract class
*/
void ServiceGenerator::generateService(const ServiceDescriptor* service, io::Printer* printer)
{
    map<string, string> vars;
    vars["ServiceName"] = service->name();

    printer->Print(vars, "import abc\n"
                          "from rpc_service_pb2 import Payload\n"
                          "class $ServiceName$(object):\n  __metaclass__ = abc.ABCMeta\n\n");
    printer->Indent();

    // Generate abstract methods (skip the error method).
    for (int i = 1; i < service->method_count(); i++) {
        vars["MethodName"] = CamelCaseToLowerCaseUnderscores(service->method(i)->name());

        printer->Print(vars, "@abc.abstractmethod\n"
                             "def $MethodName$(self, call):\n  raise Exception()\n\n");
    }

    printer->Outdent();
}

/**
  Generates the ServiceReactor class
*/
void ServiceGenerator::generateReactor(const ServiceDescriptor* service, io::Printer* printer)
{
    checkThatRpcErrorIsDefined(service);

    map<string, string> vars;

    vars["ErrorMethodEnum"] = methodNameToPythonEnum(service->method(0));
    vars["MethodEnums"] = generateMethodCallEnums(service);
    vars["ServiceName"] = service->name();

    // Output the first part of the generated data to file
    #include "ServiceReactorPart1.tpl.h"
    printer->Print(vars, (char*) ServiceReactorPart1_tpl);

    // Generate if-elif statements for each method callback.
    printer->Indent();
    printer->Indent();
    printer->Indent();

    // The first one is "if" and all subsequent statements are "elif".
    vars["IfStatement"] = "if";
    vars["ErrorMethodName"] = methodNameToPythonEnum(service->method(0));

    for (int i = 1; i < service->method_count(); i++) {

        vars["LabelName"] = methodNameToPythonEnum(service->method(i));
        vars["MethodName"] = CamelCaseToLowerCaseUnderscores(service->method(i)->name());
        vars["InputName"] = service->method(i)->input_type()->name();

        printer->Print(vars, "$IfStatement$ t == $ServiceName$Reactor._METHOD_ENUMS_.$LabelName$:\n");
        if (service->method(i)->input_type()->field_count() > 0) {
            printer->Print(vars, "  call = $InputName$.FromString(payload.payload_data)\n"
                                 "  reply = self._service.$MethodName$(call)\n\n");
        }
        else {
            printer->Print(vars, "  reply = self._service.$MethodName$()\n\n");
        }

        vars["IfStatement"] = "elif";
    }

    printer->Outdent();
    printer->Outdent();
    printer->Outdent();

    #include "ServiceReactorPart2.tpl.h"
    printer->Print(vars, (char*) ServiceReactorPart2_tpl);
    printer->Print("\n");
}

void ServiceGenerator::generateStub(const ServiceDescriptor* service, io::Printer* printer)
{
    // Ensure there are methods defined in the service
    if (service->method_count() == 0) {
        GOOGLE_LOG(FATAL) << "Error: Service " << service->name() << " has no methods. (file: " << service->file()->name() << ")";
    }

    // If the first method is not an __error__ method, fail
    if (service->method(0)->name() != "__error__") {
        GOOGLE_LOG(FATAL) << "Error: The first method in Service " << service->name() << " must be named '__error__'. (file: " << service->file()->name() << ")";
    }

    map<string, string> vars;

    // Populate map with error method information
    vars["ErrorMethodEnum"] = methodNameToPythonEnum(service->method(0));
    vars["ErrorMethodOutputMessageType"] = pythonScopedMessageName(service->file(), service->method(0)->output_type());

    // Populate map with enums for method calls
    vars["MethodEnums"] = generateMethodCallEnums(service);

    // Set the name of the RPC service
    vars["ServiceName"] = service->name();

    // Output the first part of the generated data to file
    #include "ServiceStubHeader.tpl.h"
    printer->Print(vars, (char*) ServiceStubHeader_tpl);

    // Generate method stubs
    {
        // Indent the method definitions
        printer->Indent();

        #include "RpcMethodStub.tpl.h"

        // We skip the first method because we have asserted it is the __error__ method
        // which isn't really a method but gives us a way of defining the type of the
        // error our generated code creates
        for (int i = 1; i < service->method_count(); i++) {
            generateMethodStub(service->method(i), printer, (char*) RpcMethodStub_tpl);
            printer->Print("\n");
        }

        printer->Outdent();
    }
}

/**
  Generates a python enum representing method calls, which isn't really an enum
  but looks like this:

  DO_METHOD_1, DO_METHOD_2, DO_METHOD_3 = range(3)
  */
string generateMethodCallEnums(const ServiceDescriptor* service)
{
    // Create an 'enum'-like list of method calls
    stringstream methodEnums;
    for (int i = 0; i < service->method_count(); i++) {
        if (i > 0) {
            methodEnums << ", ";
        }
        methodEnums << methodNameToPythonEnum(service->method(i));
    }
    methodEnums << " = range(" << service->method_count() << ")";
    return methodEnums.str();
}

/**
  Converts a camel case string like:

    helloWorldFromCamel

  to a Python enum, adhereing to convention:

    _HELLO_WORLD_FROM_CAMEL_
  */
string methodNameToPythonEnum(const MethodDescriptor* method)
{
    return "_" + CamelCaseToCapitalizedUnderscores(method->name()) + "_";
}

/**
  Based on a given template, generates a python method. The generated method looks something
  like the following:

    def method_name(self, param_1, param_2):
        m = InputMessage()
        m.param_1 = param_1
        m.param_2 = param_2
        d = self._sendData(INTEGER_REPRESENTING_THIS_METHOD, m.SerializeToString())
        return OutputMessage.FromString(d)
  */
void generateMethodStub(const MethodDescriptor* method, io::Printer* printer, char* methodTemplate)
{
    map<string, string> methodDefinitionVars;

    methodDefinitionVars["ServiceName"] = method->service()->name();
    methodDefinitionVars["MethodName"] = CamelCaseToLowerCaseUnderscores(method->name());
    methodDefinitionVars["MethodArgs"] = messageFieldsToParameterList(method->input_type());
    methodDefinitionVars["InputMessageType"] = pythonScopedMessageName(method->service()->file(), method->input_type());
    methodDefinitionVars["MethodCallEnum"] = methodNameToPythonEnum(method);
    methodDefinitionVars["OutputMessageType"] = pythonScopedMessageName(method->service()->file(), method->output_type());
    methodDefinitionVars["MessageFieldAssignment"] = messageFieldAssignmentToString(method->input_type());

    printer->Print(methodDefinitionVars, methodTemplate);
}

/**
  Returns a properly scoped python name for the given protobuf message type.

  If the message is defined in the same file as 'thisFile', then simply return
  the message's local name, ignoring the package name defined in the protobuf file.

  If the message is defined in another file, prepend the name of that file to the
  message's local name, in the format [filename]_pb2.[message_name]
  */
string pythonScopedMessageName(const FileDescriptor* thisFile, const Descriptor* message)
{
    string messageName = message->full_name();
    if (!message->file()->package().empty()) {
        messageName = messageName.substr(message->file()->package().length() + 1);
    }

    if (message->file()->name() == thisFile->name()) {
        return messageName;
    }

    stringstream name;
    name << stripExtension(getBaseFilename(message->file()->name()))
         << "_pb2"
         << "."
         << messageName;
    return name.str();
}

/**
  Compiles all message fields of a protobuf message into
  a python parameter list. Since python is dynamically typed, no
  types are required
  */
string messageFieldsToParameterList(const Descriptor* message)
{
    stringstream args;

    // Add the 'self' parameter
    args << "self";

    // For all fields, create a parameter list
    for (int i = 0; i < message->field_count(); i++) {
        const FieldDescriptor* field = message->field(i);
        args << ", " << field->name();
    }
    return args.str();
}

/**
  Creates a string of the form,

    m.param_1 = param_1
    m.param_2 = param_2
    ...

  for all fields of the given protobuf message type.

  The Protobuf Python API does not allow assignment of composite
  message fields, aka, if param_1 is itself a message,
  you can't assign it directly to m.param_1. Instead, we must do,

    m.param_1.CopyFrom(param_1)
  */
string messageFieldAssignmentToString(const Descriptor *message)
{
    stringstream assignment;

    // For all fields, create assignment code
    for (int i = 0; i < message->field_count(); i++) {
        const FieldDescriptor* field = message->field(i);

        assignment << endl << INDENT << INDENT;

        if (field->is_optional()) {
          // The field is optional, do not perform any assignment if the value is None
          assignment <<  "if " << field->name() << " is not None: ";
        }

        assignment << "m." << field->name();

        if (field->is_repeated()) {
            // The field is repeated, so we need to use extend()
            assignment << ".extend(" << field->name() << ")";
        } else if (field->type() == FieldDescriptor::TYPE_MESSAGE) {
            // The field is another message, so we need to do CopyFrom()
            // instead of assignment
            assignment << ".CopyFrom(" << field->name() << ")";
        } else {
            // Regular assignment, because this isn't a composite type
            assignment << " = " << field->name();
        }
    }
    return assignment.str();
}
