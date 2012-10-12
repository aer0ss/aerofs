#include <assert.h>
#include <errno.h>
#include <jni.h>
#include <stdlib.h>      /* For getenv(), malloc(), reallocf(), etc. */

#include "../launcher.lib/liblauncher.h"

#ifdef _WIN32
#include <regex>
#define DIRECTORY_SEPARATOR _T('\\')
#else
#include <signal.h>      /* for signal(3).  We need to ignore SIGPIPE. */
#include <unistd.h>      /* for readlink(2), chdir(2) */
#include <libgen.h>      /* For dirname(3) */
#define DIRECTORY_SEPARATOR '/'
#endif

#ifdef __APPLE__
#include <sys/param.h>   /* For PATH_MAX */
#include <mach-o/dyld.h> /* For _NSGetExecutablePath() */
#endif

static tstring get_approot(void);
static tstring get_executable_path(void);

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
    tstring approot = get_approot();

    // Try to move into the approot.
#ifdef _WIN32
    BOOL successful = SetCurrentDirectory(approot.c_str());
    if (!successful) {
        _tprintf(_T("Couldn't enter approot directory %s : %d\n"), approot.c_str(), GetLastError());
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

    // N.B. the GUI/CLI always pass us an absolute path, so we needn't worry about
    // expanding argv[1] == "DEFAULT" to the platform's usual rtroot location.
    tstring heap_dump_option_string = tstring(_T("-XX:HeapDumpPath=")) + tstring(argv[1]);
    _TCHAR* args[] = {const_cast<_TCHAR*>(heap_dump_option_string.c_str()), argv[1], _T("daemon"), NULL};
    bool vm_created = launcher_create_jvm(approot.c_str(), args, &jvm, &env, &errmsg);

    if (!vm_created) {
        _tprintf(_T("Error: %s\n"), errmsg);
        return EXIT_FAILURE;
    }

    int exit_code = launcher_launch(env, &errmsg);
    if (exit_code != EXIT_SUCCESS) {
        _tprintf(_T("Error: %s\n"), errmsg);
    }

    launcher_destroy_jvm(jvm);

    return exit_code;
}

// private functions

/**
  Return the approot path, without trailing slashes.
  This is the folder that contains the path to the current executable binary.
*/
static tstring get_approot(void)
{
    tstring approot = get_executable_path();
    // Truncate the path from the trailing folder separator onward
    // to turn the executable path into the dirname
    approot = approot.substr(0, approot.rfind(DIRECTORY_SEPARATOR));

    // On Windows, we now install AeroFS in a different subfolder for each version
    // Only aerofs.exe, aerofs.ini and aerofsd.exe stay at the top-level folder
    // So in order to find the approot, we have to read the current version folder from aerofs.ini

#ifdef _WIN32

    // Open aerofs.ini
    // Note: we have to use the Win32 API since the path may have Unicode characters in it
    tstring path = approot + _T("\\aerofs.ini");
    HANDLE hFile = CreateFile(path.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                              FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
        _tprintf(_T("Warning: could not open %s\n"), path.c_str());
        return approot;
    }

    // Read the first 100 chars of the file into a buffer
    char buf[100];
    DWORD bytesRead = 0;
    ReadFile(hFile, buf, sizeof(buf)-1, &bytesRead, NULL);
    CloseHandle(hFile);
    buf[bytesRead] = '\0';

    // Use a regular expression to find the current version folder
    std::regex rx("(v_[.0-9]+)");
    std::cmatch result;
    std::regex_search(buf, result, rx);
    if (result.length() < 2) {
        _tprintf(_T("Warning: could not find the current AeroFS version in %s\n"), path.c_str());
        return approot;
    }

    // Append the version folder to the approot
    std::string version(result[1]);
    approot += _T("\\") + tstring(version.begin(), version.end());

#endif

    return approot;
}

/**
 * Returns the absolute canonical path to the currently running executable.
 * There are intelligent platform-specific ways to find this:
 *  On Windows: GetModuleFileName() (with NULL hModule) gives the executable string
 *  On OSX: _NSGetExecutablePath() and realpath() are adequate
 *  On Linux: /proc/self/exe is a symlink to the actual executable
 * This may be useful in other programs.
 */
static tstring get_executable_path(void) {
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
