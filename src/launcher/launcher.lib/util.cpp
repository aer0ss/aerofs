#include "util.h"

using namespace std;

bool file_exists(const std::string& file)
{
    // TODO: We could probably use stat() instead
    if (FILE* f = fopen(file.c_str(), "r")) {
        fclose(f);
        return true;
    }

    return false;
}

bool ends_with(const string& str, const string& end)
{
    if (str.length() < end.length()) {
        return false;
    }

    return (str.substr(str.length() - end.length(), end.length()) == end);
}

/**
  Removes the file name portion of a path as well as the trailing slash
 */
string dir_name(const string& path)
{
    size_t pos = path.rfind(DIRECTORY_SEPARATOR);
    return path.substr(0, pos);
}
