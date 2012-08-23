#include <jni.h>
#include "liblauncher.h"

#include <stdlib.h>
#include <assert.h>
#include <stdio.h>
#include <vector>
#include <iostream>

#include "util.h"

using namespace std;

char g_errmsg[512];

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
tstring construct_classpath(const tstring& approot);
//tstring list_jars(const tstring& jars_path);
vector<tstring> get_default_options();
bool parse_options(_TCHAR*** pargv, vector<tstring>* options);

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
bool launcher_create_jvm(const _TCHAR* approot, _TCHAR** args, JavaVM** pjvm, JNIEnv** penv, char** perrmsg)
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
    vector<char*> utf8_strings_to_delete;
#endif
    JavaVMOption* vmopt = new JavaVMOption[options.size()];
    for (size_t i = 0; i < options.size(); i++) {
#ifdef _WIN32
        // Windows needs options converted from UTF16 to UTF8.
        // Query required buffer size
        int size = WideCharToMultiByte(CP_UTF8, // Codepage
                    0,                          // Flags
                    options.at(i).c_str(),      // UTF16 string data
                    -1,                         // # of characters, or -1 if null-terminated
                    NULL,                       // outarg UTF8 data
                    0,                          // bytes available at UTF8 data pointer
                    NULL,                       // default char (must be NULL)
                    NULL);                      // default char used (must be NULL)
        if (size == 0) {
            SET_ERROR("WideCharToMultiByte: %d\n", GetLastError());
            return false;
        }
        // Allocate buffer
        char* buffer = new char[size];
        // Perform conversion
        size = WideCharToMultiByte(CP_UTF8, // Codepage
                    0,                      // Flags
                    options.at(i).c_str(),  // UTF16 string data
                    -1,                     // # of UTF16 characters (-1 for null-terminated)
                    buffer,                 // outarg UTF8 data
                    size,                   // bytes available at UTF8 data pointer
                    NULL,                   // default char (must be NULL)
                    NULL);                  // default char used (must be NULL)
        if (size == 0) {
            SET_ERROR("WideCharToMultiByte: %d\n", GetLastError());
            return false;
        }
        // Save buffer pointer to be deleted later
        utf8_strings_to_delete.push_back(buffer);
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

    jint result = create_jvm(pjvm, (void**)penv, &vm_args);

    delete vmopt;
#ifdef _WIN32
    for (size_t i = 0; i < utf8_strings_to_delete.size() ; i++) {
        delete [] utf8_strings_to_delete.at(i);
    }
    utf8_strings_to_delete.clear();
#endif

    if (result < 0) {
        SET_ERROR("Call to JNI_CreateJavaVM failed");
        return false;
    }
    return true;
}

int launcher_launch(JNIEnv* env, char** perrmsg)
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
        SET_ERROR("Could not load class '%s'\n", class_name);
        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }
        return EXIT_FAILURE;
    }

    // Get a pointer to main()
    jmethodID mid = env->GetStaticMethodID(cls, "main", "([Ljava/lang/String;)V");
    if (mid == NULL) {
        SET_ERROR("Could not find method 'static void main(String[] args)' in class '%s'\n",
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
        SET_ERROR("An exception occurred in main()");
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

}
