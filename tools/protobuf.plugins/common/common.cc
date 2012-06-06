#include "common.h"
#include <algorithm>

using namespace std;

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

string CamelCaseToLowerCaseUnderscores(const string& input)
{
    string output = CamelCaseToCapitalizedUnderscores(input);
    std::transform(output.begin(), output.end(), output.begin(), ::tolower);
    return output;
}

void checkThatRpcErrorIsDefined(const ServiceDescriptor* service)
{
    if (service->method_count() == 0) {
        GOOGLE_LOG(FATAL) << "Error: Service " << service->name()
            << " has no methods. (file: " << service->file()->name() << ")";
    }
    if (service->method(0)->name() != "__error__") {
        GOOGLE_LOG(FATAL) << "Error: The first method in Service "
            << service->name() << " must be named '__error__'. (file: "
            << service->file()->name() << ")";
    }
}
