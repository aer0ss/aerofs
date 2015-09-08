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
  Main entry point for the aerofs gui. This launches the aerofs gui with
  the default runtime root.

  Usage:
    aerofs
 */
#ifdef _WIN32
int __stdcall WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nShowCmd)
{
    (void)hInstance;     // unused
    (void)hPrevInstance; // unused
    (void)lpCmdLine;     // unused
    (void)nShowCmd;      // unused
    SetLastError(ERROR_SUCCESS);
#else
int main(int argc, char** argv)
{
    // Util.execBackground closes all our output streams, so we need to ignore
    // SIGPIPE if we want to be able to print any debugging information
    signal(SIGPIPE, SIG_IGN);
#endif
    JavaVM* jvm;
    JNIEnv* env;
    _TCHAR* errmsg;
    _TCHAR msg[MAX_MESSAGE_LENGTH];
    _TCHAR approot[MAX_APPROOT_LENGTH];

#ifdef __APPLE__
    if (argc < 2) {
        show_error(_T("No approot given"));
        return EXIT_FAILURE;
    }
    strncpy(approot, argv[1], MAX_APPROOT_LENGTH-1);
    approot[MAX_APPROOT_LENGTH-1] = 0;
#else
    bool fromOSXBundle = false;

    if (!launcher_get_approot(approot, sizeof(approot), &errmsg, fromOSXBundle)) {
        _sprintf(msg, sizeof(msg), _T("%s\n%s"), _T("Could not find approot:"), errmsg);
        show_error(msg);
        return EXIT_FAILURE;
    }
#endif

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

    _TCHAR* args[] = {(_TCHAR *)_T("DEFAULT"), (_TCHAR *)_T("gui"), NULL};
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
 * Display the error message in a message box if Windows otherwise print.
 */
static void show_error(const _TCHAR* details)
{
    _TCHAR msg[2*MAX_MESSAGE_LENGTH];
    _sprintf(msg, sizeof(msg), _T("%s\n%s\n%s\n%s\n"),
             _T("We're sorry, AeroFS failed to launch:"), details,
             _T("Please contact support@aerofs.com to report this problem."), _T("Thank you."));
#ifdef _WIN32
    MessageBox(NULL, msg, NULL, MB_ICONERROR);
#else
    fprintf(stderr, "%s", msg);
#endif
}
