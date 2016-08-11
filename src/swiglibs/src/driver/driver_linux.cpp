#include "AeroFS.h"
#include "Driver.h"
#include <sys/vfs.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <libgen.h>
#include <dirent.h>
#include <cctype>
#include <cstdio>
#include <vector>
#include <cstdlib>          /* for atoi() */
#include <errno.h>

namespace Driver {

void initNotifications(JNIEnv* env, jstring socket) {}
void scheduleNotification(JNIEnv* env, jstring title, jstring subtitle, jstring message, jdouble delay, jstring notif_message) {}

int setFolderIcon(JNIEnv* env, jstring folderPath, jstring iconName) {
    return DRIVER_FAILURE;
}

int markHiddenSystemFile(JNIEnv* env, jstring jpath) {
    return DRIVER_FAILURE;
}

/*
 * On Linux, we shell out to stat instead of making a native call which
 * requires maintenance of a giant table of superblock -> filesystem mappings.
 */
int getFileSystemType(JNIEnv * j, jstring jpath, void* buffer, int buflen)
{
	assert(false);
	return 0;
}

/**
 * Checks whether a string of text is numeric.
 *
 * @param str The string of text to check
 * @return true if the string is numeric, false otherwise
 */
bool isNumeric(const char* str) {
    for (; *str != 0; str++) {
        if (!isdigit(*str)) {
            return false;
        }
    }
    return true;
}

// Used to kill the daemon process. Defined in driver_nix.cpp
extern bool killProcess(pid_t);

#define PROC_PATH "/proc"
#define CMDLINE_FILE "cmdline"
#define MAX_CMDLINE_LEN 1024

// A 'Double expansion' trick to get a preprocessor numeric value
// to act as a string
#define STRINGIFY2(x) #x
#define STRINGIFY(x) STRINGIFY2(x)

int killProcess(JNIEnv *env, jstring name)
{
    tstring tName;
    if (!AeroFS::jstr2tstr(&tName, env, name)) {
        return DRIVER_FAILURE;
    }
    const char *cName = tName.c_str();

    // The 3 is for NULL character and path separators
    char pathToCmdline[sizeof(PROC_PATH) + sizeof(CMDLINE_FILE) + NAME_MAX + 3];

    char cmdlineNameBuffer[MAX_CMDLINE_LEN + 1];

    // Open the /proc directory
    DIR* procDir = opendir(PROC_PATH);
    if (procDir == NULL) {
        FERROR(": no /proc on filesystem");
        return DRIVER_FAILURE;
    }

    // This vector will contain the pids of any daemon processes
    // running on the system
    std::vector<pid_t> daemonProcs;

    struct dirent* dirEntity;

    // While there are still entities in the /proc directory
    while ((dirEntity = readdir(procDir))) {
        if (dirEntity->d_type != DT_DIR || !isNumeric(dirEntity->d_name)) {
            // This is either not a directory or a process PID so we can safely
            // ignore this entity
            continue;
        }

        // Build the path to the 'cmdline' file for this process
        // d_name is a maximum of NAME_MAX characters long, so pathToCmdline
        // is allocated above such that this will 'never' overflow.
        sprintf(pathToCmdline, PROC_PATH "/%s/" CMDLINE_FILE, dirEntity->d_name);

        // Extract the name of the process from the 'cmdline' file

        // The 't' is specified for text files, and makes no difference in the common case.
        // It is mentioned that it is best practice for portability, however
        // http://www.cplusplus.com/reference/clibrary/cstdio/fopen/
        FILE* fCmdline = fopen(pathToCmdline, "rt");
        if (fCmdline == NULL) {
            continue;
        }

        // Clear the buffer and scan the first argument
        // Be careful not to overflow the buffer
        memset(cmdlineNameBuffer, 0, sizeof(cmdlineNameBuffer));
        fscanf(fCmdline, "%" STRINGIFY(MAX_CMDLINE_LEN) "s", cmdlineNameBuffer);

        // Close the file, we're done with it
        fclose(fCmdline);

        // Extract the process name from the cmdline argument path
        char* processName = basename(cmdlineNameBuffer);

        // Check to see if this process name matches the request
        if (strcmp(processName, cName) == 0) {
            daemonProcs.push_back((pid_t)atoi(dirEntity->d_name));
        }
    }

    // Finished with "/proc"
    closedir(procDir);

    bool error = false;

    // Kill all the discovered daemon processes
    std::vector<pid_t>::iterator iter = daemonProcs.begin();
    for (; iter != daemonProcs.end(); iter++) {
        FINFO(": kill " << *iter);

        // Kill it!! Kill them all!!
        error |= !killProcess(*iter);
    }

    // Return number of daemons killed if successful, otherwise DRIVER_FAILURE
    return error ? DRIVER_FAILURE : daemonProcs.size();
}

}
