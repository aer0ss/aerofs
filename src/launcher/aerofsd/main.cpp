#include <assert.h>
#include <errno.h>
#include <jni.h>

#include "../launcher.lib/liblauncher.h"
#include "../common/util.h"

#ifdef _WIN32
#else
#include <signal.h>      /* for signal(3).  We need to ignore SIGPIPE. */
#endif

static void show_error(const _TCHAR* details);

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
    _TCHAR msg[MAX_MESSAGE_LENGTH];
    _TCHAR approot[MAX_APPROOT_LENGTH];

    if (!launcher_get_approot(approot, sizeof(approot), &errmsg, false)) {
        _sprintf(msg, sizeof(msg), _T("%s\n%s"), _T("Could not find approot:"), errmsg);
        show_error(msg);
        return EXIT_FAILURE;
    }

    // Try to move into the approot.
    if (!change_dir(approot)) {
    #ifdef _WIN32
        _sprintf(msg, sizeof(msg), _T("%s %s: %d\n"), _T("Couldn't enter approot directory"), approot, GetLastError());
    #else
        _sprintf(msg, sizeof(msg), _T("%s %s: %s\n"), _T("Couldn't enter approot directory"), approot, strerror(errno));
    #endif
        show_error(msg);
        return EXIT_FAILURE;
    }

    // N.B. the GUI/CLI always pass us an absolute path, so we needn't worry about
    // expanding argv[1] == "DEFAULT" to the platform's usual rtroot location.
    tstring heap_dump_option_string = tstring(_T("-XX:HeapDumpPath=")) + tstring(argv[1]);
    _TCHAR* args[] = {const_cast<_TCHAR*>(heap_dump_option_string.c_str()), argv[1], _T("daemon"), NULL};
    bool vm_created = launcher_create_jvm(approot, args, &jvm, &env, &errmsg);

    if (!vm_created) {
        _sprintf(msg, sizeof(msg), _T("%s:\n%s"), _T("JVM creation failed"), errmsg);
        show_error(msg);
        return EXIT_FAILURE;
    }

    int exit_code = launcher_launch(env, &errmsg);
    if (exit_code != EXIT_SUCCESS) {
        _sprintf(msg, sizeof(msg), _T("%s:\n%s"), _T("JVM launch failed"), errmsg);
        show_error(msg);
    }

    launcher_destroy_jvm(jvm);

    return exit_code;
}


/**
 * Display the error message by printing it on the console.
 */
static void show_error(const _TCHAR* details) {
    _TCHAR msg[2*MAX_MESSAGE_LENGTH];
    _sprintf(msg, sizeof(msg), _T("%s\n%s\n%s\n%s\n"),
             _T("We're sorry, AeroFS failed to launch:"), details,
             _T("Please contact support@aerofs.com to report this problem."), _T("Thank you."));
#ifdef _WIN32
    _tprintf(msg);
#else
    fprintf(stderr, "%s", msg);
#endif
}
