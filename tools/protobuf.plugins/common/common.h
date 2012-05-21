#ifndef COMMON_H
#define COMMON_H

#include <string>
#include <google/protobuf/descriptor.h>

using namespace google::protobuf;

std::string CamelCaseToCapitalizedUnderscores(const std::string& input);
std::string CamelCaseToLowerCaseUnderscores(const std::string& input);
void checkThatRpcErrorIsDefined(const ServiceDescriptor* service);

#endif
