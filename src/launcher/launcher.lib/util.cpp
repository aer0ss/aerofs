#include "util.h"

using namespace std;

bool ends_with(const tstring& str, const tstring& end)
{
    if (str.length() < end.length()) {
        return false;
    }

    return (str.substr(str.length() - end.length(), end.length()) == end);
}

/**
  Removes the file name portion of a path as well as the trailing slash
 */
tstring dir_name(const tstring& path)
{
    size_t pos = path.rfind(DIRECTORY_SEPARATOR);
    return path.substr(0, pos);
}
