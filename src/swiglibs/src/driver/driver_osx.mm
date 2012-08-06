#import <Cocoa/Cocoa.h>

#include "AeroFS.h"
#include "Driver.h"
#include <sys/param.h>
#include <sys/mount.h>
#include <errno.h>

namespace Driver {

void setFolderIcon(JNIEnv* env, jstring folderPath, jstring iconName)
{
    tstring cPath;
    tstring cIconName;

    if (!(AeroFS::jstr2tstr(&cPath, env, folderPath) &&
          AeroFS::jstr2tstr(&cIconName, env, iconName))) {
        return;
    }

    NSAutoreleasePool* pool = [[NSAutoreleasePool alloc] init];
    NSString* nsPath = [[NSString alloc] initWithUTF8String:cPath.c_str()];
    NSString* nsIconName = [[NSString alloc] initWithUTF8String:cIconName.c_str()];

    NSImage* icon = nil;
    if (nsIconName.length > 0) {
        icon = [[NSImage alloc] initWithContentsOfFile:nsIconName];

        if (!icon) {
            NSLog(@"Warning: icon not found: %@", nsIconName);
        }
    }

    [[NSWorkspace sharedWorkspace] setIcon:icon forFile:nsPath options:0];
    [nsPath release];
    [nsIconName release];
    [icon release];
    [pool drain];
}

/*
 * Places a string in buffer representing the filesystem type of the file at
 * the given path.  This may be something like "ext4", "nfs", or "btrfs".
 *
 * Returns <0 on failure, FS_LOCAL if the filesystem is local, and FS_REMOTE if
 * the filesystem is remote.
 */
int getFileSystemType(JNIEnv * j, jstring jpath, void* buffer, int buflen)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) return -ENOENT;
    struct statfs stfs;
    int rc = 0;
    // statfs(2) retrieves filesystem information for the given path
    rc = statfs(path.c_str(), &stfs);
    if (rc != 0) {
        int errsv = errno;
        FERROR(": " << errsv);
        return -errsv;
    }
    // OSX provides the name of the filesystem in the statfs struct.  How nice!
    // Verify that the fs name fits in the given buffer
    if (strlen(stfs.f_fstypename) + 1 > buflen) {
        return -ENOSPC;
    }
    // Copy the fs name into the buffer
    strcpy((char*)buffer, stfs.f_fstypename);
    if (stfs.f_flags & MNT_LOCAL) {
        return FS_LOCAL;
    } else {
        return FS_REMOTE;
    }
}

}
