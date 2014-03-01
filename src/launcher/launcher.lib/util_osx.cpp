#include "util.h"

#include <unistd.h>
#include <dirent.h>
#include <assert.h>
#include <dlfcn.h>
#include <stdio.h> /* debug only */

extern _TCHAR g_errmsg[256];

using namespace std;

bool create_jvm(const tstring& approot, JavaVM **pvm, void **penv, void *args)
{
    void* handle;
    // Prototype of JNI_CreateJavaVM, which we will dlsym
    static jint (*JNI_CreateJavaVM_func)(JavaVM **pvm, void **penv, void *args);

    // Load libjli first.  Apple's JavaRuntimeSupport framework that libjvm
    // will load checks for the availability of JLI_MemAlloc via dlsym(), and
    // if not available, then it will prompt the user to install a JRE.  We
    // want to avoid this. If we dlopen libjli before loading libjvm, then OSX
    // will quietly comply with our wishes.  So load libjli first.
    //
    // This is also why we do not link against libjvm directly on OSX - when
    // libjvm loads, the "is a system JRE installed" check happens too soon.
    // We can't fool JavaRuntimeSuport without dlopen().
    tstring libjli = approot + _T("/jre/lib/jli/libjli.dylib");
    fprintf(stderr, "Attempting to dlopen libjli...\n");
    handle = dlopen(libjli.c_str(), RTLD_LAZY);
    if (!handle) {
        fprintf(stderr, "%s\n", dlerror());
        SET_ERROR("Could not dlopen %s: %s", libjli.c_str(), dlerror());
        return false;
    }
    fprintf(stderr, "Loaded libjli.\n");

    // Load libjvm
    tstring libjvm = approot + _T("/jre/lib/server/libjvm.dylib");
    fprintf(stderr, "Attemptint to dlopen libjvm...\n");
    handle = dlopen(libjvm.c_str(), RTLD_LAZY);
    if (!handle) {
        fprintf(stderr, "%s\n", dlerror());
        SET_ERROR("Could not dlopen %s: %s", libjvm.c_str(), dlerror());
        return false;
    }
    fprintf(stderr, "Loaded libjvm.\n");

    // Find JNI_CreateJavaVM in libjvm.
    JNI_CreateJavaVM_func = (jint (*)(JavaVM**, void**, void*)) (dlsym(handle, "JNI_CreateJavaVM"));
    char* err = dlerror();
    if (err) {
        SET_ERROR("Error finding JNI_CreateJavaVM: %s", err);
        return false;
    }

    // Actually instantiate the JVM.
    jint result = JNI_CreateJavaVM_func(pvm, penv, args);
    if (result < 0) {
        SET_ERROR(_T("Call to JNI_CreateJavaVM failed with error code: %d."), result);
        return false;
    }
    return true;
}

