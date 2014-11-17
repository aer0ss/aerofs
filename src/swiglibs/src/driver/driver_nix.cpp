#ifdef __APPLE__
#include <AvailabilityMacros.h>

#if !defined(AVAILABLE_MAC_OS_X_VERSION_10_5_AND_LATER)
#error "This file system required Leopard and above."
#endif

#define __FreeBSD__ 10
#define _GNU_SOURCE
#endif //__APPLE__

#include <dlfcn.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <memory>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/resource.h> // for getrusage()

#include "../logger.h"
#include "AeroFS.h"
#include "Driver.h"

#define NSEC_PER_SEC 1000000000
#define NSEC_PER_USEC 1000

using namespace std;

namespace Driver {

/**
 * Kills the process with the given PID. Blocks until either
 * the process was successfully killed or there was an error
 * killing the process.
 *
 * @param pid The PID of the process to kill
 * @return true if the process was killed, false otherwise
 */
bool killProcess(pid_t pid)
{
    kill(pid, SIGKILL);

    while (true) {
        if (kill(pid, 0)) {
            if (errno == ESRCH) {
                // the process has exited
                break;
            } else {
                FERROR(": kill(pid, 0) returns error " << errno);
                return false;
            }
        }
        sleep(1);
    }

    return true;
}

int getFidLength()
{
    return sizeof(ino_t);
}

/**
 * Return either DRIVER_FAILURE or one of GETFID_* constant
 */
int getFid(JNIEnv * j, jstring jpath, void * buffer)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) return DRIVER_FAILURE;

    // don't follow symlinks
    struct stat st;
    if (lstat(path.c_str(), &st)) {
        // save errno before piping it into "<<", since that operator may
        // change the value as a side-effect.
        int errsv = errno;
        FERROR(": " << errsv);
        PACK_ERROR_IN(buffer, errsv);
        return DRIVER_FAILURE_WITH_ERRNO;
    }

    mode_t type = st.st_mode & S_IFMT;
    if (type == S_IFLNK) return GETFID_SYMLINK;
    else if (type != S_IFREG && type != S_IFDIR) {
        return GETFID_SPECIAL;
    }

    if (buffer) {
        // put ino before dev to speed up fid comparison
        bcopy(&st.st_ino, buffer, sizeof(ino_t));
    }

    return type == S_IFREG ? GETFID_FILE : GETFID_DIR;
}

int replaceFile(JNIEnv * j, jstring replacement, jstring replaced, jstring backup)
{
    // stub for Windows-only function
    return DRIVER_FAILURE;
}

/**
 * Return the size (in bytes) of a unique mount identifier (dev_t).
 */
int getMountIdLength()
{
    return sizeof(dev_t);
}

/**
 * Place the mount id (dev_t) for the file associated with the given path in
 * buffer.
 *
 * Returns <0 on failure, 0 on success.
 */
int getMountIdForPath(JNIEnv * j, jstring jpath, void* buffer)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) return -ENOENT;

    struct stat st;
    if (lstat(path.c_str(), &st)) {
        // The file isn't there, error
        int errsv = errno; // Save errno; logging calls may change it
        FERROR(": " << errsv);
        return -errsv;
    }
    // Copy the (unique) device identifier for that inode into the buffer and
    // return
    memcpy(buffer, &st.st_dev, sizeof(st.st_dev));
    return 0;
}

int waitForNetworkInterfaceChange()
{
    return DRIVER_FAILURE;

    // to implement on OSX: http://stackoverflow.com/questions/2910121/network-connection-nsnotification-for-osx
    // to implement on Linux: http://stackoverflow.com/questions/2261759/get-notified-about-network-interface-change-on-linux
}

TrayPosition getTrayPosition()
{
    // Windows only - return nothing on *nix
    TrayPosition result = {};
    return result;
}

CpuUsage getCpuUsage()
{
    CpuUsage retval;
    int res;
    struct rusage usage;
    res = getrusage(RUSAGE_SELF, &usage);
    if (res == -1) {
        // Realistically, this should never happen.
        retval.kernel_time = -1;
        retval.user_time = errno;
        return retval;
    }

    retval.kernel_time = (usage.ru_stime.tv_sec * NSEC_PER_SEC) + (usage.ru_stime.tv_usec * NSEC_PER_USEC);
    retval.user_time = (usage.ru_utime.tv_sec * NSEC_PER_SEC) + (usage.ru_utime.tv_usec * NSEC_PER_USEC);
    return retval;
}

int getUserUid()
{
    return geteuid();
}

}//namespace Driver
