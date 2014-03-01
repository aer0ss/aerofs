#include <errno.h>
#include <jni.h>
#include "liblauncher.h"

#include <stdlib.h>
#include <assert.h>
#include <stdio.h>
#include <vector>
#include <iostream>
#include <string>

#include "util.h"

#ifdef _WIN32
#define DIRECTORY_SEPARATOR _T('\\')
#else
#include <libgen.h>      /* For dirname(3) */
#define DIRECTORY_SEPARATOR '/'
#endif

#ifdef __APPLE__
#include <sys/param.h>   /* For PATH_MAX */
#include <mach-o/dyld.h> /* For _NSGetExecutablePath() */
#endif

#ifdef __GNUC__
// Disable the warning for the %hs format specifier
#pragma GCC diagnostic ignored "-Wformat"
#endif

using namespace std;

_TCHAR g_errmsg[512];

namespace {

// AeroFS constants
static const _TCHAR* const AEROFS_JAR = _T("aerofs.jar");
static const _TCHAR* const BIN_DIR = _T("bin");
static const _TCHAR* const JARS_DIR = _T("lib");
static const char* const AEROFS_MAIN_CLASS = "com/aerofs/Main";

// Global variables
static _TCHAR** g_args;

// internal functions
int launch(JNIEnv* env, const char* class_name, _TCHAR* argv[]);
bool get_executable_path(tstring& path, tstring& errmsg);
tstring construct_classpath(const tstring& approot);
vector<tstring> get_default_options();
bool parse_options(_TCHAR*** pargv, vector<tstring>* options);

}

/**
  Return the approot path, without trailing slashes.
  This is the folder that contains the path to the current executable binary.
*/
bool launcher_get_approot(_TCHAR* approot, size_t approot_len, _TCHAR** perrmsg, bool fromOSXBundle)
{
    *perrmsg = g_errmsg;
    tstring s_approot;
    tstring errmsg;
    if (!get_executable_path(s_approot, errmsg)) {
        SET_ERROR(_T("Could not get the executable path %s\n"), errmsg.c_str());
        return false;
    }
    // Truncate the path from the trailing folder separator onward
    // to turn the executable path into the dirname
    s_approot = s_approot.substr(0, s_approot.rfind(DIRECTORY_SEPARATOR));

    if (fromOSXBundle) {
        // For aerofs (but not aerofsd) on OS X, approot is in ../Resources/Java relative to the executable
        s_approot = s_approot.substr(0, s_approot.rfind(DIRECTORY_SEPARATOR));
        s_approot += "/Resources/Java";
    }

    // On Windows, we now install AeroFS in a different subfolder for each version
    // Only executables and the version file stay at the top-level folder
    // So in order to find the approot, we have to read the current version folder from version

#ifdef _WIN32

    // Open version
    // Note: we have to use the Win32 API since the path may have Unicode characters in it
    tstring path = s_approot + _T("\\version");
    HANDLE hFile = CreateFile(path.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                              FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
        SET_ERROR(_T("Could not open %s\n"), path.c_str());
        _tcscpy_s(approot, approot_len, s_approot.c_str());
        return false;
    }

    // Read the first 100 chars of the file into a buffer
    char buf[20];
    DWORD bytesRead = 0;
    ReadFile(hFile, buf, sizeof(buf)-1, &bytesRead, NULL);
    CloseHandle(hFile);
    buf[bytesRead] = '\0';

    // Keep only the first line
    std::string version(buf);
    size_t pos = version.find_first_of("\r\n\t ");
    if (pos != string::npos) {
        version = version.substr(0, pos);
    }

    // Append the version folder to the approot, unless the version number is empty or equals to
    // 100.0.0, which is the version number for the development environment.
    // This version number must be kept consistent with the populate script in /tools/populate/
    if (!version.empty() && version != "100.0.0") {
        s_approot += _T("\\v_") + tstring(version.begin(), version.end());
    }

#endif
    _tcscpy_s(approot, approot_len, s_approot.c_str());
    return true;
}


/**
  Creates a JVM
  The calling thread will become the Java main thread
  Arguments:
    - approot: path to approot, without a trailing slash
    - args: command line arguments
    - pjvm: [out] will reveive the pointer to the created JavaVM, or NULL if error
    - penv: [out] will reveive the pointer to the created JNIEnv, or NULL if error
    - perrmsg: [out] will receive a pointer to a string describing any error

   Returns true if the JVM was created successfully.

   See also: launcher_destroy_jvm()
 */
bool launcher_create_jvm(const _TCHAR* approot, _TCHAR** args, JavaVM** pjvm, JNIEnv** penv, _TCHAR** perrmsg)
{
    *perrmsg = g_errmsg;
    *pjvm = NULL;
    *penv = NULL;

    vector<tstring> options = get_default_options();

    // parse the command line arguments
    // arguments starting with a dash are added as JVM options
    // g_args is updated to point to the first non-dash argument,
    // (those will be passed to the java main())
    g_args = args;
    if (!parse_options(&g_args, &options)) {
        return false;
    }

    tstring t_approot(approot);
    tstring classpath = construct_classpath(t_approot);
    options.push_back(classpath);

#ifdef _WIN32
    vector<char*> ansi_strings_to_delete;
#endif
    JavaVMOption* vmopt = new JavaVMOption[options.size()];
    for (size_t i = 0; i < options.size(); i++) {
#ifdef _WIN32
        // Windows needs options converted from UTF16 to <whatever the default codepage is>.
        // I wish this was UTF8, but it turns out it varies by system and I haven't figured out
        // a way to make java use UTF8 as the default codepage.
        // So for now, we use the native ANSI codepage, and assume that the user's username will
        // be something that can be properly represented by characters in that codepage.
        // (And if it isn't, then the NSIS installer won't work either, so it's a non-issue.)

        // Get the size of the output buffer needed to perform the conversion
        int size = WideCharToMultiByte(CP_ACP,  // Codepage (default ANSI codepage)
                    0,                          // Flags
                    options.at(i).c_str(),      // UTF16 string data
                    -1,                         // # of characters, or -1 if null-terminated
                    NULL,                       // outarg multibyte data
                    0,                          // bytes available at data pointer
                    NULL,                       // default char (must be NULL)
                    NULL);                      // default char used (must be NULL)
        if (size == 0) {
            SET_ERROR(_T("WideCharToMultiByte: %d\n"), GetLastError());
            return false;
        }
        // Allocate an appropriately-sized buffer
        char* buffer = new char[size];
        // Perform conversion
        size = WideCharToMultiByte(CP_ACP,  // Codepage (default ANSI codepage)
                    0,                      // Flags
                    options.at(i).c_str(),  // UTF16 string data
                    -1,                     // # of UTF16 characters (-1 for null-terminated)
                    buffer,                 // outarg multibyte data
                    size,                   // bytes available at data pointer
                    NULL,                   // default char (must be NULL)
                    NULL);                  // default char used (must be NULL)
        if (size == 0) {
            SET_ERROR(_T("WideCharToMultiByte: %d\n"), GetLastError());
            return false;
        }
        // Save buffer pointer to be deleted later
        ansi_strings_to_delete.push_back(buffer);
        // Set option string
        vmopt[i].optionString = buffer;
#else
        vmopt[i].optionString = (char*)(options.at(i).c_str());
#endif
    }

    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_6;
    vm_args.options = vmopt;
    vm_args.nOptions = options.size();
    vm_args.ignoreUnrecognized = JNI_FALSE;

#ifdef DEBUG
    for (unsigned int i = 0 ; i < options.size() ; i++) {
        _ftprintf(stderr, _T("%s\n"), options[i].c_str());
    }
#endif

    bool result = create_jvm(approot, pjvm, (void**)penv, &vm_args);

    delete vmopt;
#ifdef _WIN32
    for (size_t i = 0; i < ansi_strings_to_delete.size() ; i++) {
        delete [] ansi_strings_to_delete.at(i);
    }
    ansi_strings_to_delete.clear();
#endif

    return result;
}

int launcher_launch(JNIEnv* env, _TCHAR** perrmsg)
{
    *perrmsg = g_errmsg;
    return launch(env, AEROFS_MAIN_CLASS, g_args);
}

/**
  Must be called form the same thread as launcher_create_jvm
 */
void launcher_destroy_jvm(JavaVM* jvm)
{
    if (jvm) {
        jvm->DestroyJavaVM();
    }
}

// Internal functions
namespace {
vector<tstring> get_default_options()
{
    vector<tstring> result;
    result.push_back(_T("-ea"));
    result.push_back(_T("-Xmx64m"));
    result.push_back(_T("-XX:+UseConcMarkSweepGC"));
    result.push_back(_T("-XX:+HeapDumpOnOutOfMemoryError"));
    result.push_back(_T("-Djava.net.preferIPv4Stack=true"));
    return result;
}

int launch(JNIEnv* env, const char* class_name, _TCHAR* argv[])
{
    assert(strlen(class_name) < 128);  // sanity check on class_name

    jclass cls = env->FindClass(class_name);
    if (!cls) {
        // We have to use %hs because class_name is char* but the error string is wchar on Windows
        // This creates a warning with gcc (but it works as expected)
        SET_ERROR(_T("Could not load class '%hs'\n"), class_name);
        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }
        return EXIT_FAILURE;
    }

    // Get a pointer to main()
    jmethodID mid = env->GetStaticMethodID(cls, "main", "([Ljava/lang/String;)V");
    if (mid == NULL) {
        SET_ERROR(_T("Could not find method 'static void main(String[] args)' in class '%hs'\n"),
                  class_name);
        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }
        return EXIT_FAILURE;
    }

    // Create a new String array and initialize it with the strings in argv
    int argc = 0;
    while (argv[argc] != NULL) { argc++; }
    jobjectArray args = env->NewObjectArray(argc, env->FindClass("java/lang/String"), NULL);
    for (int i = 0; i < argc; i++) {
#ifdef _WIN32
        env->SetObjectArrayElement(args, i, env->NewString(argv[i], _tcslen(argv[i])));
#else
        env->SetObjectArrayElement(args, i, env->NewStringUTF(argv[i]));
#endif
    }

    // Call main()
    env->CallStaticVoidMethod(cls, mid, args);

    if (env->ExceptionOccurred()) {
        SET_ERROR(_T("An exception occurred in main()"));
        env->ExceptionDescribe();
        return EXIT_FAILURE; // TODO: Return a different exit code
    }

    return EXIT_SUCCESS;
}

/**
  Parses the command line arguments
  All optional arguments (ie: args starting with a '-') are passed to the JVM

  Stops when a non-optional argument is found.
  argv is updated to point to it.

  returns false if the launch should not proceed.
*/
bool parse_options(_TCHAR*** pargv, vector<tstring>* options)
{
    _TCHAR** argv = *pargv;
    _TCHAR* arg;

    while ((arg = *argv) != NULL && arg[0] == _T('-')) {
        options->push_back(arg);
        argv++;
    }

    *pargv = argv;
    return true;
}

/**
  Returns a string with the classpath
*/
tstring construct_classpath(const tstring& approot)
{
    tstring aerofs_classes = approot + DIRECTORY_SEPARATOR + AEROFS_JAR;
    if (!file_exists(aerofs_classes)) {
        aerofs_classes = approot + DIRECTORY_SEPARATOR + BIN_DIR;
    }

    tstring classpath = _T("-Djava.class.path=") + aerofs_classes;

    // Append the path to each jar to classpath
    const tstring jars_path = approot + DIRECTORY_SEPARATOR + JARS_DIR + DIRECTORY_SEPARATOR;
    classpath += list_jars(jars_path);

    return classpath;
}

/**
 * Returns the absolute canonical path to the currently running executable.
 * There are intelligent platform-specific ways to find this:
 *  On Windows: GetModuleFileName() (with NULL hModule) gives the executable string
 *  On OSX: _NSGetExecutablePath() and realpath() are adequate
 *  On Linux: /proc/self/exe is a symlink to the actual executable
 * This may be useful in other programs.
 */
bool get_executable_path(tstring& path, tstring& errmsg)
{
#ifdef _WIN32
    _TCHAR buffer[MAX_PATH];
    GetModuleFileName(NULL, buffer, MAX_PATH);
    DWORD err = GetLastError();
    if (err != ERROR_SUCCESS) {
        _TCHAR details[100];
        _sprintf(details, 100, _T("%d"), err);
        errmsg = tstring(details);
        return false;
    }
    path = tstring(buffer);
    return true;
#endif
#ifdef __APPLE__
    uint32_t buflen = 0;
    char* path_buf = NULL;
    // when buflen is too small to hold the string, buflen is set to how much buffer is needed
    _NSGetExecutablePath(path_buf, &buflen);
    path_buf = (char*)reallocf(path_buf, buflen);
    if (!path_buf) {
        // Out of memory.
        errmsg = _T("Out of memory");
        return false;
    }
    // Actually retrieve the executable path this time
    int rc = _NSGetExecutablePath(path_buf, &buflen);
    if (rc == -1) {
        int errsv = errno;
        char details[100];
        _sprintf(details, 100, _T("%s %d"), _T("_NSGetExecutablePath failed "), errsv);
        errmsg = tstring(details);
        return false;
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
    path = tstring(path_buf);
    free(path_buf);
    return true;
#endif
#ifdef __linux__
#define MAX_PATH 1024
    char c_path[MAX_PATH];
    ssize_t path_len;
    path_len = readlink("/proc/self/exe", c_path, MAX_PATH);
    if (path_len == -1 || path_len == MAX_PATH) {
        // MAX_PATH: We might have truncated the path.
        // -1:  Some other error, which really shouldn't happen for
        //      /proc/self/exe.
        if (path_len == -1) {
            errmsg = _T("readlink error: ");
            errmsg += _T(strerror(errno));
        } else {
            errmsg = _T("readlink() likely truncated (very long path to executable?)");
        }
        return false;
    }
    // Append null terminator to path
    c_path[path_len] = '\0';
    path = tstring(path);
    return true;
#undef MAX_PATH
#endif
}

}
