#include "util.h"

using namespace std;

// Utility functions

/**
 * Sets the current directory to {@code path}.
 * @return true if successfully changed directory, false otherwise.
 */
bool change_dir(const _TCHAR* path)
{
#ifdef _WIN32
    BOOL successful = SetCurrentDirectory(path);
    if (!successful) {
        return false;
    }
#else
    int rc = chdir(path);
    if (rc == -1) {
        return false;
    }
#endif
    return true;
}
