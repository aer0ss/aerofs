#ifndef AEROFS_DRIVER_H_
#define AEROFS_DRIVER_H_

#include <jni.h>
#include "../logger.h"

namespace Driver {

#define DRIVER_FAILURE_WITH_ERRNO      -2
#define DRIVER_FAILURE      -1
#define DRIVER_SUCCESS      0

#define GETFID_FILE         1
#define GETFID_DIR          2
#define GETFID_SYMLINK      3
#define GETFID_SPECIAL      4

#define FS_LOCAL            1
#define FS_REMOTE           2

#define DAEMON_PROC_NAME    "aerofsd"

/**
 * Initializes the C++ logger.
 *
 * @param rtRoot The user's AeroFS runtime root
 * @param name The name of the module
 * @param loglevel The level at which to log
 */
void initLogger_(jstring rtRoot, jstring name, LogLevel loglevel);

/**
 * Kills any daemon processes active on the system. If
 * the kill is successful, this call blocks until the
 * process has completely exitted.
 *
 * @return DRIVER_FAILURE if the call failed to kill a daemon process, otherwise
 *         returns the number of daemon processes killed.
 */
int killDaemon();

/**
 * Returns the length of the system file identifier in bytes.
 *
 * @return length of FID in bytes
 */
int getFidLength();

/**
 * @param buffer null to test file type only; otherwise its length must be equal
 * to the return value of getFidLength(). the buffer is filled only if the
 * method returns GETFID_FILE or GETFID_DIR.
 */
int getFid(JNIEnv * j, jstring path, void * buffer);

/**
 * Windows-only, wrapper for ReplaceFile
 *
 * http://msdn.microsoft.com/en-us/library/windows/desktop/aa365512(v=vs.85).aspx
 */
int replaceFile(JNIEnv * j, jstring replaced, jstring replacement, jstring backup);

/**
 * Return the size of a mount-unique identifier, in bytes.
 * Only used on OSX and Linux.
 *
 * @return The size of dev_t, in bytes
 */
int getMountIdLength();

/**
 * Block until the network interfaces have changed.
 *
 * @return DRIVER_FAILURE if registering for interface change notifcations fails or is not supported.
 * @return DRIVER_SUCCESS if network interface change has happened.
 */
int waitForNetworkInterfaceChange();

/**
 * Fill buffer with the mount-unique identifier associated with the file
 * referred to in path.
 * Only used on OSX and Linux.
 *
 * @param buffer Output argument to be filled with the actual dev_t entry.
 * @return 0 on success; <0 on failure.
 */
int getMountIdForPath(JNIEnv * j, jstring path, void * buffer);

/**
 * Place a string in the provided buffer which represents the name of the
 * filesystem backing the named given file (e.g. "ext4", "btrfs", "ntfs")
 *
 * @param path file for which the caller wants to know the underlying filesystem
 * @param buffer Output argument to be filled with the actual filesystem string
 * @param bufLen length of the caller-provided buffer.
 * @return <0 on failure, FS_LOCAL if the filesystem is local, and FS_REMOTE if the filesystem is remote.
 */
int getFileSystemType(JNIEnv * j, jstring path, void * buffer, int bufLen);

/**
 * Set or remove a custom icon on a folder
 * @param folderPath: absolute path to a folder.
 * @param iconName: platform-specific string identifying the icon
 *
 *   OSX: a path to an icns file. This path will be passed to NSImage initWithContentsOfFile:
 *
 *   Windows: A path to an icon (potentially including an icon index). This path will be set into
 *   the desktop.ini file.
 *
 * Set iconName to an empty string to reset the icon to the default system icon.
 */
void setFolderIcon(JNIEnv * j, jstring folderPath, jstring iconName);

struct TrayPosition {
    int x;
    int y;
    enum {Top, Right, Bottom, Left} orientation;
};

TrayPosition getTrayPosition();

struct CpuUsage {
    long long kernel_time; // Nanoseconds of time spent in kernel mode (syscalls)
    long long user_time; // Nanoseconds of time spent in user mode (regular usage)
};

/**
 * Retrieve the current process's CPU usage, as measured by the platform.
 * On error, the return value's kernel_time field will be negative and user_time will contain the
 * platform error code.
 */
CpuUsage getCpuUsage();

}

#endif //AEROFS_DRIVER_H_
