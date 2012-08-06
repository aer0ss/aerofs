#include <jni.h>
#include "liblauncher.h"

#include <stdlib.h>
#include <assert.h>
#include <stdio.h>
#include <vector>

#if WIN32
#include "dirent_win.h"
#define snprintf sprintf_s
#else
#include <dirent.h>
#endif

#include "util.h"

using namespace std;

namespace {

// AeroFS constants
static const char* const AEROFS_JAR = "aerofs.jar";
static const char* const BIN_DIR = "bin";
static const char* const JARS_DIR = "lib";
static const char* const AEROFS_MAIN_CLASS = "com/aerofs/Main";

// Other constants
static const char* const JAR_EXTENSION = ".jar";

// Global variables
static char** g_args;
static char g_errmsg[512];

// internal functions
int launch(JNIEnv* env, const char* class_name, char* argv[]);
string construct_classpath(const string& approot);
string list_jars(const string& jars_path);
vector<string> get_default_options();
bool parse_options(char*** pargv, vector<string>* options);

}

#define SET_ERROR(...) snprintf(g_errmsg, sizeof(g_errmsg), ##__VA_ARGS__);

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
bool launcher_create_jvm(const char* approot, char** args, JavaVM** pjvm, JNIEnv** penv, char** perrmsg)
{
    *perrmsg = g_errmsg;
    *pjvm = NULL;
    *penv = NULL;

    vector<string> options = get_default_options();

    // parse the command line arguments
    // arguments starting with a dash are added as JVM options
    // g_args is updated to point to the first non-dash argument,
    // (those will be passed to the java main())
    g_args = args;
    if (!parse_options(&g_args, &options)) {
        return false;
    }

    string classpath = construct_classpath(approot);
    options.push_back(classpath);

    JavaVMOption* vmopt = new JavaVMOption[options.size()];
    for (size_t i = 0; i < options.size(); i++) {
        vmopt[i].optionString = const_cast<char*>(options.at(i).c_str());
    }

    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_6;
    vm_args.options = vmopt;
    vm_args.nOptions = options.size();
    vm_args.ignoreUnrecognized = JNI_FALSE;

    jint result = create_jvm(pjvm, (void**)penv, &vm_args);

    delete vmopt;

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
vector<string> get_default_options()
{
    vector<string> result;
    result.push_back("-ea");
    result.push_back("-Xmx64m");
    result.push_back("-XX:+UseConcMarkSweepGC");
    result.push_back("-XX:+HeapDumpOnOutOfMemoryError");
    result.push_back("-Djava.net.preferIPv4Stack=true");

    return result;
}

int launch(JNIEnv* env, const char* class_name, char* argv[])
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
        env->SetObjectArrayElement(args, i, env->NewStringUTF(argv[i]));
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
bool parse_options(char*** pargv, vector<string>* options)
{
    char** argv = *pargv;
    char* arg;

    while ((arg = *argv) != NULL && arg[0] == '-') {
        options->push_back(arg);
        argv++;
    }

    *pargv = argv;
    return true;
}

/**
  Returns a string with the classpath
*/
string construct_classpath(const string& approot)
{
    string aerofs_classes = approot + DIRECTORY_SEPARATOR + AEROFS_JAR;
    if (!file_exists(aerofs_classes.c_str())) {
        aerofs_classes = approot + DIRECTORY_SEPARATOR + BIN_DIR;
    }

    string classpath = "-Djava.class.path=" + aerofs_classes;

    // Append the path to each jar to classpath
    const string jars_path = approot + DIRECTORY_SEPARATOR + JARS_DIR + DIRECTORY_SEPARATOR;
    classpath += list_jars(jars_path);

    return classpath;
}

/**
  Return a string with the path of all *.jar files at `jars_path` concatenated
  Important:
    - jars_path must include a trailing slash
 */
string list_jars(const string& jars_path)
{
    assert(jars_path[jars_path.length() - 1] == DIRECTORY_SEPARATOR);
    string result;

    DIR* dir = opendir(jars_path.c_str());
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir))) {
            string jar(entry->d_name);
            if (!ends_with(jar, JAR_EXTENSION)) {
                continue;
            }
            result += PATH_SEPARATOR + jars_path + jar;
        }
        closedir(dir);
    } else {
        SET_ERROR("Warning: could not open directory: %s\n", jars_path.c_str());
    }

    return result;
}
}
