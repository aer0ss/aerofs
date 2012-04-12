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
