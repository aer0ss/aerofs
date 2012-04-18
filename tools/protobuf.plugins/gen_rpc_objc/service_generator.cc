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
string methodSignature(const Descriptor* input_type);
string methodSignatureUsingBlocks(const Descriptor* input_type, const Descriptor* output_type);
void methodSignatureHelper(stringstream& signature, const Descriptor* input_type);
string getTypeName(const FieldDescriptor* field);
string methodEnumName(const MethodDescriptor* method);

void ServiceGenerator::generateStubHeader(const ServiceDescriptor* service, io::Printer* printer)
{
    if (service->method_count() == 0) {
        GOOGLE_LOG(FATAL) << "Error: Service " << service->name() << " has no methods. (file: " << service->file()->name() << ")";
    }
    if (service->method(0)->name() != "__error__") {
        GOOGLE_LOG(FATAL) << "Error: The first method in Service " << service->name() << " must be named '__error__'. (file: " << service->file()->name() << ")";
    }

    map<string, string> vars;
    vars["ServiceStubName"] = ClassName(service) + "Stub";
    vars["ErrorClass"] = ClassName(service->method(0)->output_type());

    #include "ServiceStubHeader.tpl.h"
    printer->Print(vars, (char*) ServiceStubHeader_tpl);

    // Start at 1 because the first method is the __error__ method
    for (int i = 1; i < service->method_count(); i++) {
        const MethodDescriptor* method = service->method(i);

        map<string, string> vars;
        vars["methodName"] = UnderscoresToCamelCase(method);
        vars["signature1"] = methodSignature(method->input_type());
        vars["signature2"] = methodSignatureUsingBlocks(method->input_type(), method->output_type());

        printer->Print(vars,
                       "- (void) $methodName$$signature1$;\n"
                       "- (void) $methodName$$signature2$;\n"
                       );
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

    // Output the other switch cases
    for (int i = 0; i < service->method_count(); i++) {
        map<string, string> vars;
        vars["LabelName"] = methodEnumName(service->method(i));
        vars["ReplyClassName"] = ClassName(service->method(i)->output_type());

        if (i == 0) {
            // Output the switch case to deal with errors
            printer->Print(vars,
                           "case $LabelName$: {\n"
                           "  error = [delegate decodeError:[$ReplyClassName$ parseFromData:[payload payloadData]]];\n"
                           "  break;\n"
                           "}\n");
        } else {
            printer->Print(vars,
                           "case $LabelName$: {\n"
                           "  reply = [$ReplyClassName$ parseFromData: [payload payloadData]];\n"
                           "  break;\n"
                           "}\n");
        }
    }

    printer->Outdent();
    printer->Outdent();

    #include "ServiceStubImplPart2.tpl.h"
    printer->Print(vars, (char*) ServiceStubImplPart2_tpl);

    // Output the method stubs
    for (int i = 1; i < service->method_count(); i++) {
        generateMethodStub(service->method(i), printer);
    }

    printer->Print("@end");
}

///////////////////////////////////////////////////////////////////////////////////////////////
// Helper functions
///////////////////////////////////////////////////////////////////////////////////////////////

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
    vars["signature1"] = methodSignature(method->input_type());
    vars["signature2"] = methodSignatureUsingBlocks(method->input_type(), method->output_type());
    vars["CallType"] = ClassName(method->input_type());
    vars["CallEnumConstant"] = methodEnumName(method);
    vars["callSetters"] = setters.str();

    printer->Print(vars,
                   // selector / object callback
                   "\n"
                   "- (void)$methodName$$signature1$\n"
                   "{\n"
                   "  $CallType$_Builder* call = [$CallType$ builder];\n"
                   "$callSetters$"
                   "\n"
                   "  Payload* payload = [[[[Payload builder] setType:$CallEnumConstant$] setPayloadData:[[call build] data]] build];\n"
                   "  [delegate sendBytes: [payload data]param1:(id)selector param2:object];\n"
                   "}\n"

                   // block callback
                   "\n"
                   "- (void)$methodName$$signature2$\n"
                   "{\n"
                   "  $CallType$_Builder* call = [$CallType$ builder];\n"
                   "$callSetters$"
                   "\n"
                   "  Payload* payload = [[[[Payload builder] setType:$CallEnumConstant$] setPayloadData:[[call build] data]] build];\n"
                   "  [delegate sendBytes: [payload data]param1:[block copy] param2:nil];\n"
                   "}\n"
                   );
}

/**
  Helper method to get the signature string using the selector / object callback mechanism.

  For example, given the following protobuf message:
  @code
    message AddPersonCall {
        required Person person = 1;
        optional int32 another_field = 2;
    }
  @endcode

  it returns:
    @codeline ":(Person*)person withAnotherField:(int32_t)anotherField andPerform:(SEL)selector withObject:(id)object"
*/
string methodSignature(const Descriptor* input_type)
{
    stringstream signature;
    methodSignatureHelper(signature, input_type);
    const char* a = (input_type->field_count() > 0) ? "a" : "A";
    signature << a << "ndPerform:(SEL)selector withObject:(id)object";
    return signature.str();
}

/**
  Helper method to get the signature string taking a block to perform the callback.

  For example, given the following protobuf message:
  @code
    message AddPersonCall {
        required Person person = 1;
        optional int32 another_field = 2;
    }
  @endcode

  it returns:
    @codeline ":(Person*)person withAnotherField:(int32_t)anotherField usingBlock:(void(^)(ReplyClass* reply, NSError* error))block"
*/
string methodSignatureUsingBlocks(const Descriptor* input_type, const Descriptor* output_type)
{
    stringstream signature;
    methodSignatureHelper(signature, input_type);
    string argName = (input_type->field_count() > 0) ? "usingBlock" : "";
    signature << argName << ":(void(^)(" << ClassName(output_type) << "* reply, NSError* error))block";
    return signature.str();
}

void methodSignatureHelper(stringstream& signature, const Descriptor* message)
{
    // Add all call fields to the signature

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
