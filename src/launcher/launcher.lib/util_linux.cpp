#include "util.h"

#include <unistd.h>
#include <dirent.h>
#include <assert.h>

extern _TCHAR g_errmsg[256];

using namespace std;

bool create_jvm(const tstring& approot, JavaVM **pvm, void **penv, void *args)
{
    jint result = JNI_CreateJavaVM(pvm, penv, args);
    if (result < 0) {
        SET_ERROR(_T("Call to JNI_CreateJavaVM failed with error code: %d."), result);
        return false;
    }
    return true;
}
