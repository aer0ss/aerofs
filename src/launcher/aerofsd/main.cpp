#include <assert.h>
#include <errno.h>
#include <jni.h>
#include <stdlib.h>      /* For getenv(), malloc(), reallocf(), etc. */

#include "../launcher.lib/liblauncher.h"

#ifndef _WIN32
#include <signal.h>      /* for signal(3).  We need to ignore SIGPIPE. */
#include <unistd.h>      /* for readlink(2), chdir(2) */
#include <libgen.h>      /* For dirname(3) */
#endif

#ifdef __APPLE__
#include <sys/param.h>   /* For PATH_MAX */
#include <mach-o/dyld.h> /* For _NSGetExecutablePath() */
#endif

#ifdef _WIN32
#define DIRECTORY_SEPARATOR _T('\\')
#else
#define DIRECTORY_SEPARATOR '/'
#endif

static tstring get_approot();
static tstring get_executable_path();
// TODO (DF): reenable once we figure out how to deal with the obfuscated jar better
// static bool set_approot_static_member(JNIEnv* env, tstring path);

/**
  Main entry point for the aerofs daemon.  This launches the aerofs daemon with
  the specified runtime root.

  Usage:
    aerofsd <rtroot>

 */
#ifdef _WIN32
int _tmain(int argc, _TCHAR* argv[])
{
    SetLastError(ERROR_SUCCESS);
#else
int main(int argc, char* argv[])
{
    // Util.execBackground closes all our output streams, so we need to ignore
    // SIGPIPE if we want to be able to print any debugging information
    signal(SIGPIPE, SIG_IGN);
#endif
    if (argc != 2) {
        _tprintf(_T("Usage: %s <RTROOT>\n"), argv[0]);
        return EXIT_FAILURE;
    }

    JavaVM* jvm;
    JNIEnv* env;
    _TCHAR* errmsg;
    _TCHAR* args[] = {argv[1], _T("daemon"), NULL};
    tstring approot = get_approot();

    // Try to move into the approot.
#ifdef _WIN32
    BOOL successful = SetCurrentDirectory(approot.c_str());
    if (!successful) {
        _tprintf(_T("Couldn't enter approot directory %ls : %d\n"), approot.c_str(), GetLastError());
        return EXIT_FAILURE;
    }
#else
    int rc = chdir(approot.c_str());
    if (rc == -1) {
        char* msg = strerror(errno);
        printf("Couldn't enter approot directory %s : %s\n", approot.c_str(), msg);
        return EXIT_FAILURE;
    }
#endif

    bool vm_created = launcher_create_jvm(approot.c_str(), args, &jvm, &env, &errmsg);

    if (!vm_created) {
        printf("Error: %s\n", errmsg);
        return EXIT_FAILURE;
    }

    // set AppRoot's static field to the appropriate value through JNI
    // TODO (DF): uncomment this when we have it working
    /*
    bool approot_set_successfully = set_approot_static_member(env, approot);
    if (!approot_set_successfully) {
        printf("Couldn't set AppRoot._abs\n");
        return EXIT_FAILURE;
    }
    */

    int exit_code = launcher_launch(env, &errmsg);
    if (exit_code != EXIT_SUCCESS) {
        printf("Error: %s\n", errmsg);
    }

    launcher_destroy_jvm(jvm);

    return exit_code;
}

// private functions

/**
  Return the approot path, without trailing slashes.
  This is the folder that contains the path to the current executable binary.
*/
static tstring get_approot()
{
    tstring retval = get_executable_path();
    // Truncate the path from the trailing folder separator onward
    // to turn the executable path into the dirname
    retval = retval.substr(0, retval.rfind(DIRECTORY_SEPARATOR));
    return retval;
}

/**
 * Returns the absolute canonical path to the currently running executable.
 * There are intelligent platform-specific ways to find this:
 *  On Windows: GetModuleFileName() (with NULL hModule) gives the executable string
 *  On OSX: _NSGetExecutablePath() and realpath() are adequate
 *  On Linux: /proc/self/exe is a symlink to the actual executable
 * This may be useful in other programs.
 */
static tstring get_executable_path() {
#ifdef _WIN32
    _TCHAR buffer[MAX_PATH];
    GetModuleFileName(NULL, buffer, MAX_PATH);
    DWORD err = GetLastError();
    if (err != ERROR_SUCCESS) {
        printf("Couldn't get executable absolute path: %d\n", err);
        exit(EXIT_FAILURE);
    }
    return tstring(buffer);
#endif
#ifdef __APPLE__
    uint32_t buflen = 0;
    char* path_buf = NULL;
    // when buflen is too small to hold the string, buflen is set to how much buffer is needed
    _NSGetExecutablePath(path_buf, &buflen);
    path_buf = (char*)reallocf(path_buf, buflen);
    if (!path_buf) {
        // Out of memory.
        exit(EXIT_FAILURE);
    }
    // Actually retrieve the executable path this time
    int rc = _NSGetExecutablePath(path_buf, &buflen);
    if (rc == -1) {
        int errsv = errno;
        printf("_NSGetExecutablePath: %d\n", errsv);
        exit(EXIT_FAILURE);
    }
    // Canonicalize absolute path.  We can't pass NULL as the second argument to realpath()
    // to let it allocate the output buffer because that convenience didn't exist until OSX 10.6,
    // and we currently support OSX 10.5.
    char* canonical_path_buf = (char*)malloc(PATH_MAX * sizeof(char));
    char* executable_canonical_path = realpath(path_buf, canonical_path_buf);
    if (executable_canonical_path) {
        // All's well.  Replace path_buf with the canonical path.
        free(path_buf);
        path_buf = executable_canonical_path;
    } else {
        // Something went wrong.  Clean up canonical_path_buf, and return the uncanonicalized path.
        free(canonical_path_buf);
    }
    tstring retval(path_buf);
    free(path_buf);
    return retval;
#endif
#ifdef __linux__
#define MAX_PATH 1024
    char path[MAX_PATH];
    ssize_t path_len;
    path_len = readlink("/proc/self/exe", path, MAX_PATH);
    if (path_len == -1 || path_len == MAX_PATH) {
        // MAX_PATH: We might have truncated the path.
        // -1:  Some other error, which really shouldn't happen for
        //      /proc/self/exe.
        if (path_len == -1) {
            perror("readlink");
        } else {
            printf("readlink() likely truncated (very long path to executable?)\n");
        }
        exit(EXIT_FAILURE);
    }
    // Append null terminator to path
    path[path_len] = '\0';
    return tstring(path);
#undef MAX_PATH
#endif
}

/**
 * Sets the static variable AppRoot._abs to the given path via JNI.
 * This is useful because AppRoot is still doing guesswork about paths
 */
/* Disabled until we figure out how to deal with obfuscation sanely.
   com.aerofs.lib.AppRoot doesn't exist as a name in the obfuscated jars. - (DF)
bool set_approot_static_member(JNIEnv* env, tstring path)
{
    char* class_name = "com/aerofs/lib/AppRoot";
    char* member_name = "_abs";
    char* member_sig = "Ljava/lang/String;";
    jclass cls = env->FindClass(class_name);
    if (!cls) {
        fprintf(stderr, "Could not load class '%s'\n", class_name);
        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }
        return false;
    }
    jfieldID field_id = env->GetStaticFieldID(cls, member_name, member_sig);
    if (!field_id) {
        fprintf(stderr, "Could not get field id for '%s' (type '%s')\n", member_name, member_sig);
        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }
        return false;
    }
    // Construct new jstring containing the absolute path to the approot
#ifdef _WIN32
    jstring jpath = env->NewString(path.c_str(), path.length());
#else
    jstring jpath = env->NewStringUTF(path.c_str());
#endif
    // Set AppRoot._abs to that string.
    env->SetStaticObjectField(cls, field_id, jpath);
    return true;
}
*/
