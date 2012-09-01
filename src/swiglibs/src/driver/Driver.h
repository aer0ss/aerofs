#ifndef AEROFS_DRIVER_H_
#define AEROFS_DRIVER_H_

#include <jni.h>
#include "../logger.h"

namespace Driver {

void initLogger_(jstring rtRoot, jstring name, LogLevel loglevel);

int getPid();

// block until it knows for sure the process has quit
// return false if killing failed
bool killProcess(int pid);

int getFidLength();

#define DRIVER_FAILURE          -1
#define DRIVER_SUCCESS          0

#define GETFID_FILE         1
#define GETFID_DIR          2
#define GETFID_SYMLINK      3
#define GETFID_SPECIAL      4

#define FS_LOCAL            1
#define FS_REMOTE           2

/**
 * @param buffer null to test file type only; otherwise its length must be equal
 * to the return value of getFidLength(). the buffer is filled only if the
 * method returns GETFID_FILE or GETFID_DIR.
 */
int getFid(JNIEnv * j, jstring path, void * buffer);

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
}

#endif //AEROFS_DRIVER_H_
