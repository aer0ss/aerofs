#include "util.h"

#include <unistd.h>
#include <dirent.h>
#include <assert.h>
static const _TCHAR* const JAR_EXTENSION = _T(".jar");

extern _TCHAR g_errmsg[256];

using namespace std;

/**
  This file implements functions from util.h
  that are common to both Mac OS X and Linux.
*/

bool file_exists(const std::string& file)
{
    // TODO: We could probably use stat() instead
    if (FILE* f = fopen(file.c_str(), "r")) {
        fclose(f);
        return true;
    }
    return false;
}

/**
  Return a string with the path of all *.jar files at `jars_path` concatenated
  Important:
    - jars_path must include a trailing slash
 */
tstring list_jars(const tstring& jars_path)
{
    assert(jars_path[jars_path.length() - 1] == DIRECTORY_SEPARATOR);
    tstring result;

    DIR* dir = opendir(jars_path.c_str());
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir))) {
            tstring jar(entry->d_name);
            if (!ends_with(jar, JAR_EXTENSION)) {
                continue;
            }
            result += PATH_SEPARATOR + jars_path + jar;
        }
        closedir(dir);
    } else {
        SET_ERROR(_T("Warning: could not open directory: %s\n"), jars_path.c_str());
    }

    return result;
}
