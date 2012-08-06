#include <string>
#include <assert.h>
#include <errno.h>
#include <jni.h>

#include "../launcher.lib/liblauncher.h"
#include "../launcher.lib/util.h"

// Those paths are relative to approot:
#if MACOSX
#define AEROFS_GUI_PROD       "/../MacOS/AeroFS"
#define AEROFS_GUI_STAGING    "/AeroFS.app/Contents/MacOS/AeroFS"

#elif LINUX
#define AEROFS_GUI_PROD       "/aerofs-gui"
#define AEROFS_GUI_STAGING    "/aerofs-gui"

#elif WIN32
#define AEROFS_GUI_PROD       "\\aerofs-gui.exe"
#define AEROFS_GUI_STAGING    "\\aerofs-gui.exe"

#endif

using namespace std;

namespace {
bool launch_gui(char* argv[]);
string get_approot();
}

/**
  Main entry point for the aerofs launcher

  Usage:
    aerofs [optional jvm arguments] rtroot prog

    optional jvm arguments:
      Any optional argument (ie: arguments that start with a '-') will be passed to the JVM.
      They usually start with '-X', except for a few such as '-ea'.
      Any unrecognized argument will result in an error message and the launch being aborted.

    rtroot:
      path to rt root, or DEFAULT

    prog:
      aerofs program to launch. (daemon, sh, gui, etc..)
      refer to com.aerofs.Main for a list of supported programs

 */
int main(int argc, char* argv[])
{
    assert(argc > 0);

    bool should_launch_gui =
            argc == 1                               // no arguments where specified
            || *argv[argc - 1] == '-'               // or last orgument starts with a '-'
            || strcmp(argv[argc - 1], "gui") == 0 ;  // or last argument is "gui"

    if (should_launch_gui) {
        return launch_gui(argv) ? EXIT_SUCCESS : EXIT_FAILURE;
    }

    string approot = get_approot();

    JavaVM* jvm;
    JNIEnv* env;
    char* errmsg;
    bool vm_created = launcher_create_jvm(approot.c_str(), (argv+1), &jvm, &env, &errmsg);

    if (!vm_created) {
        printf("Error: %s\n", errmsg);
        return EXIT_FAILURE;
    }

    int exit_code = launcher_launch(env, &errmsg);
    if (exit_code != EXIT_SUCCESS) {
        printf("Error: %s\n", errmsg);
    }

    launcher_destroy_jvm(jvm);

    return exit_code;
}

// private functions

namespace {

/**
  Return the approot path, without trailing slashes
*/
string get_approot()
{
    return ".";  // Approot is the working directory the launcher
}

bool launch_gui(char* argv[])
{
    // Try to find the gui at the prod location, fallback on staging
    // Currently, those locations only actually differ on OS X
    string aerofs_gui = get_approot() + AEROFS_GUI_PROD;
    if (!file_exists(aerofs_gui)) {
        aerofs_gui = get_approot() + AEROFS_GUI_STAGING;
    }

    // Start the gui process
    char* oldArgv0 = argv[0];
    argv[0] = const_cast<char*>(aerofs_gui.c_str());
    int pid = spawn(aerofs_gui, argv);
    argv[0]= oldArgv0;

    // Check for erros
    if (pid == -1) {
        string reason;
        switch(errno) {
        case ENOENT:
            reason = "File not found";
            break;
        case EISDIR:
        case ENOEXEC:
            reason = "File is not an executable";
            break;
        case E2BIG:
            reason = "Argument list too long";
            break;
        }
        fprintf(stderr, "Unable to launch '%s'.\nError code: %i\n%s\n", aerofs_gui.c_str(), errno, reason.c_str());
        return false;
    }

    return true;
}

}
