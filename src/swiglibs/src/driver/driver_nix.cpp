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

#include "../logger.h"
#include "AeroFS.h"
#include "Driver.h"

using namespace std;

namespace Driver {

int getPid()
{
    assert(sizeof(int) == sizeof(pid_t));
    return getpid();
}

bool killProcess(int pid)
{
    assert(sizeof(int) == sizeof(pid_t));
    kill(pid, SIGKILL);

    while (true) {
        if (kill(pid, 0)) {
            if (errno == ESRCH) {
                // the process has exited
                break;
            } else {
                FERROR(" kill(pid, 0) returns error " << errno);
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

int getFid(JNIEnv * j, jstring jpath, void * buffer)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) return GETFID_ERROR;

    // don't follow symlinks
    struct stat st;
    if (lstat(path.c_str(), &st)) {
        FERROR(": " << errno);
        return GETFID_ERROR;
    }

    mode_t type = st.st_mode & S_IFMT;
    if (type == S_IFLNK) return GETFID_IS_SYMLINK;
    else if (type != S_IFREG && type != S_IFDIR) {
        return GETFID_IS_SPECIAL;
    }

    if (buffer) {
        // put ino before dev to speed up fid comparison
        bcopy(&st.st_ino, buffer, sizeof(ino_t));
    }

    return type == S_IFREG ? GETFID_FILE : GETFID_DIR;
}

/*
 * Returns the size (in bytes) of a unique mount identifier (dev_t).
 */
int getMountIdLength()
{
    return sizeof(dev_t);
}

/*
 * Places the mount id (dev_t) for the file associated with the given path in
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

}//namespace Driver
