#include "service_generator.h"

#include <sstream>

#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/printer.h>
#include <google/protobuf/compiler/objc/objc_helpers.h>
#include <google/protobuf/stubs/strutil.h>

using namespace google::protobuf;
using namespace google::protobuf::compiler::objectivec;

// Private helper functions:
void generateMethodStub(const MethodDescriptor* method, io::Printer* printer);
void generateStubSwitchCase(const MethodDescriptor* method, io::Printer* printer);
string methodSignature(const Descriptor* message);
string getTypeName(const FieldDescriptor* field);
string methodEnumName(const MethodDescriptor* method);

void ServiceGenerator::generateStubHeader(const ServiceDescriptor* service, io::Printer* printer)
{
    map<string, string> vars;
    vars["ServiceStubName"] = ClassName(service) + "Stub";

    #include "ServiceStubHeader.tpl.h"
    printer->Print(vars, (char*) ServiceStubHeader_tpl);

    for (int i = 0; i < service->method_count(); i++) {
        const MethodDescriptor* method = service->method(i);

        map<string, string> vars;
        vars["methodName"] = UnderscoresToCamelCase(method);
        vars["signature"] = methodSignature(method->input_type());

        printer->Print(vars,
                       "- (void) $methodName$$signature$andPerform:(SEL)selector withObject:(id)object;\n");
    }

    printer->Print("@end");
}

void ServiceGenerator::generateStubImpl(const ServiceDescriptor* service, io::Printer* printer)
{
    // For each rpc method, create an enum value (ie: addPerson -> kAddPerson)
    stringstream enumRpcTypes;
    for (int i = 0; i < service->method_count(); i++) {
        if (i > 0) {
            enumRpcTypes << ",\n  ";
        }
        enumRpcTypes << methodEnumName(service->method(i));
    }

    map<string, string> vars;
    vars["ServiceStubName"] = ClassName(service) + "Stub";
    vars["EnumRpcTypes"] = enumRpcTypes.str();

    #include "ServiceStubImplPart1.tpl.h"
    printer->Print(vars, (char*) ServiceStubImplPart1_tpl);

    printer->Indent();
    printer->Indent();

    // Output the body of the switch statement
    for (int i = 0; i < service->method_count(); i++) {
        generateStubSwitchCase(service->method(i), printer);
    }

    printer->Outdent();
    printer->Outdent();

    #include "ServiceStubImplPart2.tpl.h"
    printer->Print(vars, (char*) ServiceStubImplPart2_tpl);

    // Output the method stubs
    for (int i = 0; i < service->method_count(); i++) {
        generateMethodStub(service->method(i), printer);
    }

    printer->Print("@end");
}

///////////////////////////////////////////////////////////////////////////////////////////////
// Helper functions
///////////////////////////////////////////////////////////////////////////////////////////////

void generateStubSwitchCase(const MethodDescriptor* method, io::Printer* printer)
{
    map<string, string> vars;
    vars["LabelName"] = methodEnumName(method);
    vars["ReplyClassName"] = ClassName(method->output_type());

    printer->Print(vars,
                   "case $LabelName$: {\n"
                   "  $ReplyClassName$* reply = [$ReplyClassName$ parseFromData: [payload payloadData]];\n");

    for (int i = 0; i < method->output_type()->field_count(); i++) {
        map<string, string> subvars;
        subvars["argName"] = UnderscoresToCamelCase(method->output_type()->field(i));
        subvars["type"] = getTypeName(method->output_type()->field(i));
        subvars["index"] = SimpleItoa(i + 2);

        printer->Print(subvars,
                       "  $type$ $argName$ = [reply $argName$];\n"
                       "  [invocation setArgument:&$argName$ atIndex:$index$];\n");
    }

    printer->Print("  break;\n"
                   "}\n");
}

/**
  Generates the actual method stub that gets called by the client
*/
void generateMethodStub(const MethodDescriptor* method, io::Printer* printer)
{
    stringstream setters;
    for (int j = 0; j < method->input_type()->field_count(); j++) {
        const FieldDescriptor* field = method->input_type()->field(j);
        setters << "  [call set" << UnderscoresToCapitalizedCamelCase(field)
                << ":" << UnderscoresToCamelCase(field) << "];\n";
    }

    map<string, string> vars;
    vars["methodName"] = UnderscoresToCamelCase(method);
    vars["signature"] = methodSignature(method->input_type());
    vars["CallType"] = ClassName(method->input_type());
    vars["CallEnumConstant"] = methodEnumName(method);
    vars["callSetters"] = setters.str();

    printer->Print(vars,
                   "\n"
                   "- (void) $methodName$$signature$andPerform:(SEL)selector withObject:(id)object\n"
                   "{\n"
                   "  $CallType$_Builder* call = [$CallType$ builder];\n"
                   "$callSetters$"
                   "\n"
                   "  Payload* payload = [[[[Payload builder] setType:$CallEnumConstant$] setPayloadData:[[call build] data]] build];\n"
                   "  [delegate sendBytes: [payload data]withSelector:selector andObject:object];\n"
                   "}\n");
}

/**
  Helper method to get a signature string.
  For example, given the following protobuf message:
  @code
    message AddPersonCall {
        required Person person = 1;
        optional int32 another_field = 2;
    }
  @endcode

  it returns:
    @codeline ":(Person*)person withAnotherField:(int32_t)anotherField "
    (note the space at the end of the signature)
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

        if (i > 0) {
            signature << "with" << UnderscoresToCapitalizedCamelCase(field);
        }
        signature << ":"
                  << "(" << getTypeName(field) << ")"
                  << UnderscoresToCamelCase(field) << " ";
    }
    return signature.str();
}

// Stolen from objc_primitive_field.cc
const char* PrimitiveTypeName(const FieldDescriptor* field)
{
    switch (field->type()) {
    case FieldDescriptor::TYPE_INT32   : return "int32_t" ;
    case FieldDescriptor::TYPE_UINT32  : return "uint32_t";
    case FieldDescriptor::TYPE_SINT32  : return "int32_t" ;
    case FieldDescriptor::TYPE_FIXED32 : return "uint32_t";
    case FieldDescriptor::TYPE_SFIXED32: return "int32_t" ;
    case FieldDescriptor::TYPE_INT64   : return "int64_t" ;
    case FieldDescriptor::TYPE_UINT64  : return "uint64_t";
    case FieldDescriptor::TYPE_SINT64  : return "int64_t" ;
    case FieldDescriptor::TYPE_FIXED64 : return "uint64_t";
    case FieldDescriptor::TYPE_SFIXED64: return "int64_t" ;
    case FieldDescriptor::TYPE_FLOAT   : return "Float32" ;
    case FieldDescriptor::TYPE_DOUBLE  : return "Float64" ;
    case FieldDescriptor::TYPE_BOOL    : return "BOOL"    ;
    case FieldDescriptor::TYPE_STRING  : return "NSString*";
    case FieldDescriptor::TYPE_BYTES   : return "NSData*"  ;
    default                            : return NULL;
    }
}

/**
  Returns the Objective-C type name for a field
  Works with both primitive and non-primitive types
*/
string getTypeName(const FieldDescriptor* field)
{
    ObjectiveCType type = GetObjectiveCType(field);
    switch (type) {
    case OBJECTIVECTYPE_MESSAGE:
        return ClassName(field->message_type()) + "*";
    case OBJECTIVECTYPE_ENUM:
        return ClassName(field->enum_type());
    default:
        return string(PrimitiveTypeName(field));
    }
}

/**
  Returns the enum name that is used refer to a given method
*/
string methodEnumName(const MethodDescriptor* method)
{
    return "k" + UnderscoresToCapitalizedCamelCase(method->name());
}
