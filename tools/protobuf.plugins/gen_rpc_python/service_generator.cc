#include "service_generator.h"

#include <sstream>

#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/printer.h>
#include <google/protobuf/stubs/strutil.h>

#include "util.h"
#include "common/common.h"

using namespace google::protobuf;

#define INDENT "  "

void assertRequiredField(const FieldDescriptor *field);
void generateMethodStub(const MethodDescriptor* service, io::Printer* printer, char* methodTemplate);
string generateMethodCallEnums(const ServiceDescriptor* service);
string methodNameToPythonEnum(const MethodDescriptor* name);
string pythonScopedMessageName(const FileDescriptor* thisFile, const Descriptor* message);
string requiredMessageFieldsToParameterList(const Descriptor* message);
string requiredMessageFieldAssignmentToString(const Descriptor* message);

void ServiceGenerator::generateStubImpl(const ServiceDescriptor* service, io::Printer* printer)
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

    def methodName(self, required_param_1, required_param_2):
        m = InputMessage()
        m.required_param_1 = required_param_1
        m.required_param_2 = required_param_2
        d = self._sendData(INTEGER_REPRESENTING_THIS_METHOD, m.SerializeToString())
        return OutputMessage.FromString(d)
  */
void generateMethodStub(const MethodDescriptor* method, io::Printer* printer, char* methodTemplate)
{
    map<string, string> methodDefinitionVars;

    methodDefinitionVars["ServiceName"] = method->service()->name();
    methodDefinitionVars["MethodName"] = method->name();
    methodDefinitionVars["MethodArgs"] = requiredMessageFieldsToParameterList(method->input_type());
    methodDefinitionVars["InputMessageType"] = pythonScopedMessageName(method->service()->file(), method->input_type());
    methodDefinitionVars["MethodCallEnum"] = methodNameToPythonEnum(method);
    methodDefinitionVars["OutputMessageType"] = pythonScopedMessageName(method->service()->file(), method->output_type());
    methodDefinitionVars["MessageFieldAssignment"] = requiredMessageFieldAssignmentToString(method->input_type());

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
  Compiles all required message fields of a protobuf message into
  a python parameter list. Since python is dynamically typed, no
  types are required
  */
string requiredMessageFieldsToParameterList(const Descriptor* message)
{
    stringstream args;

    // Add the 'self' parameter
    args << "self";

    // For all fields, ensure they are required and create a parameter list
    for (int i = 0; i < message->field_count(); i++) {
        const FieldDescriptor* field = message->field(i);
        assertRequiredField(field);

        args << ", " << field->name();
    }
    return args.str();
}

/**
  Creates a string of the form,

    m.required_param_1 = required_param_1
    m.required_param_2 = required_param_2
    ...

  for all required fields of the given protobuf message type.

  The Protobuf Python API does not allow assignment of composite
  message fields, aka, if required_param_1 is itself a message,
  you can't assign it directly to m.required_param_1. Instead, we
  must do,

    m.required_param_1.CopyFrom(required_param_1)
  */
string requiredMessageFieldAssignmentToString(const Descriptor *message)
{
    stringstream assignment;

    // Insert a newline so that the code insertion happens on a non-indented
    // new line. That way all the assignment statements generated below
    // will be treated the same, i.e require indentation
    assignment << "\n";

    // For all fields, ensure they are required and create assignment code
    for (int i = 0; i < message->field_count(); i++) {
        const FieldDescriptor* field = message->field(i);
        assertRequiredField(field);

        assignment << INDENT << INDENT << "m." << field->name();

        if (field->type() == FieldDescriptor::TYPE_MESSAGE) {
            // The field is another message, so we need to do CopyFrom()
            // instead of assignment
            assignment << ".CopyFrom(" << field->name() << ")" << endl;
        } else {
            // Regular assignment, because this isn't a composite type
            assignment << " = " << field->name() << endl;
        }
    }
    return assignment.str();
}

void assertRequiredField(const FieldDescriptor* field)
{
    if (!field->is_required()) {
        GOOGLE_LOG(FATAL) << "\n"
                          << "All RPC input message fields must be required.\n"
                          << "  field: " << field->name() << "\n"
                          << "  in message: " << field->containing_type()->name() << "\n";
    }
}
