#import <Cocoa/Cocoa.h>

#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdlib.h>
#include <sys/mount.h>
#include <sys/param.h>
#include <sys/sysctl.h>

#include "AeroFS.h"
#include "Driver.h"
#include "notif.h"

@interface NotificationDelegate : NSObject <NSUserNotificationCenterDelegate>
@end

@implementation NotificationDelegate {
    NSString *socket_path;
    int socket;
}

- (id) initWithSocketPath:(NSString *)socketPath
{
    self = [super init];
    if (self) {
        socket = -1;
        socket_path = socketPath;
    }
    return self;
}

- (void) userNotificationCenter:(NSUserNotificationCenter *)center didDeliverNotification:(NSUserNotification *)notification
{}

- (BOOL) userNotificationCenter:(NSUserNotificationCenter *)center shouldPresentNotification:(NSUserNotification *)notification
{
    return YES;
}

- (void) userNotificationCenter:(NSUserNotificationCenter *)center didActivateNotification:(NSUserNotification *)notification
{
    if ([notification.userInfo objectForKey:@"notif"] != nil) {
        NSLog(@"sending notif event");
        if (socket == -1) {
            socket = init_socket([socket_path UTF8String]);
        }
        if (socket != -1) {
            send_message(socket, [notification.userInfo[@"notif"] UTF8String]);
        }
    }
}
@end

namespace Driver {

NotificationDelegate *delegate = nil;

void initNotifications(JNIEnv* env, jstring socket) {
    tstring cSocket;
    if (!AeroFS::jstr2tstr(&cSocket, env, socket)) {
        NSLog(@"failed to convert socket path");
        return;
    }
    delegate = [[NotificationDelegate alloc] initWithSocketPath: [[NSString alloc] initWithUTF8String:cSocket.c_str()]];
}

void scheduleNotification(JNIEnv* env, jstring title, jstring subtitle, jstring message, jdouble delay, jstring notif_message) {
    tstring cTitle;
    tstring cSubtitle;
    tstring cMessage;

    if (!(AeroFS::jstr2tstr(&cTitle, env, title) &&
          AeroFS::jstr2tstr(&cSubtitle, env, subtitle) &&
          AeroFS::jstr2tstr(&cMessage, env, message))) {
        NSLog(@"failed to convert notif strings");
        return;
    }

    NSUserNotificationCenter *ns = [NSUserNotificationCenter defaultUserNotificationCenter];
    if (ns == nil) {
        NSLog(@"no notif center");
        return;
    }

    [ns setDelegate:delegate];

    NSLog(@"sending notif");
    NSUserNotification *notif = [[NSUserNotification alloc] init];
    [notif setTitle:[[NSString alloc] initWithUTF8String:cTitle.c_str()]];
    [notif setSubtitle:[[NSString alloc] initWithUTF8String:cSubtitle.c_str()]];
    [notif setInformativeText:[[NSString alloc] initWithUTF8String:cMessage.c_str()]];
    [notif setSoundName:NSUserNotificationDefaultSoundName];

    if (notif_message != NULL) {
        tstring cNotif;
        AeroFS::jstr2tstr(&cNotif, env, notif_message);

        NSString *jNotif = [[NSString alloc] initWithUTF8String:cNotif.c_str()];
        [notif setUserInfo:@{@"notif":jNotif}];
    }

    [notif setDeliveryDate:[[NSDate date] dateByAddingTimeInterval:delay]];
    [ns scheduleNotification:notif];
    [notif release];
}

int setFolderIcon(JNIEnv* env, jstring folderPath, jstring iconName)
{
    tstring cPath;
    tstring cIconName;

    if (!(AeroFS::jstr2tstr(&cPath, env, folderPath) &&
          AeroFS::jstr2tstr(&cIconName, env, iconName))) {
        return DRIVER_FAILURE;
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
    return DRIVER_SUCCESS;
}

int markHiddenSystemFile(JNIEnv* env, jstring path) {
    return DRIVER_FAILURE;
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

typedef struct kinfo_proc kinfo_proc;

/**
 * Taken from http://developer.apple.com/legacy/mac/library/#qa/qa2001/qa1123.html
 *
 * Returns a list of all BSD processes on the system.  This routine
 * allocates the list and puts it in *procList and a count of the
 * number of entries in *procCount.  You are responsible for freeing
 * this list (use "free" from System framework).
 * On success, the function returns 0.
 * On error, the function returns a BSD errno value.
 */
static int GetBSDProcessList(kinfo_proc **procList, size_t *procCount)
{
    int                 err;
    kinfo_proc *        result;
    bool                done;
    static const int    name[] = { CTL_KERN, KERN_PROC, KERN_PROC_ALL, 0 };
    // Declaring name as const requires us to cast it when passing it to
    // sysctl because the prototype doesn't include the const modifier.
    size_t              length;

    assert( procList != NULL);
    assert(*procList == NULL);
    assert(procCount != NULL);

    *procCount = 0;

    // We start by calling sysctl with result == NULL and length == 0.
    // That will succeed, and set length to the appropriate length.
    // We then allocate a buffer of that size and call sysctl again
    // with that buffer.  If that succeeds, we're done.  If that fails
    // with ENOMEM, we have to throw away our buffer and loop.  Note
    // that the loop causes use to call sysctl with NULL again; this
    // is necessary because the ENOMEM failure case sets length to
    // the amount of data returned, not the amount of data that
    // could have been returned.

    result = NULL;
    done = false;
    do {
        assert(result == NULL);

        // Call sysctl with a NULL buffer.

        length = 0;
        err = sysctl( (int *) name, (sizeof(name) / sizeof(*name)) - 1,
                      NULL, &length,
                      NULL, 0);
        if (err == -1) {
            err = errno;
        }

        // Allocate an appropriately sized buffer based on the results
        // from the previous call.

        if (err == 0) {
            result = (kinfo_proc*)malloc(length);
            if (result == NULL) {
                err = ENOMEM;
            }
        }

        // Call sysctl again with the new buffer.  If we get an ENOMEM
        // error, toss away our buffer and start again.

        if (err == 0) {
            err = sysctl( (int *) name, (sizeof(name) / sizeof(*name)) - 1,
                          result, &length,
                          NULL, 0);
            if (err == -1) {
                err = errno;
            }
            if (err == 0) {
                done = true;
            } else if (err == ENOMEM) {
                assert(result != NULL);
                free(result);
                result = NULL;
                err = 0;
            }
        }
    } while (err == 0 && ! done);

    // Clean up and establish post conditions.

    if (err != 0 && result != NULL) {
        free(result);
        result = NULL;
    }
    *procList = result;
    if (err == 0) {
        *procCount = length / sizeof(kinfo_proc);
    }

    assert( (err == 0) == (*procList != NULL) );

    return err;
}

// Used to kill the daemon process. Defined in driver_nix.cpp
extern bool killProcess(pid_t);

int killProcess(JNIEnv *env, jstring name)
{
    tstring tName;
    if (!AeroFS::jstr2tstr(&tName, env, name)) {
        return DRIVER_FAILURE;
    }
    const char* cName = tName.c_str();

    kinfo_proc* procList = NULL;
    size_t len;

    // Retrieve the list of active processes
    if (GetBSDProcessList(&procList, &len) != 0) {
        FERROR(": Error occurred retrieving BSD process list");
        exit(1);
    }

    int numDaemonsKilled = 0;
    bool error = false;
    for (size_t i = 0; i < len; i++) {

        if (strcmp(procList[i].kp_proc.p_comm, cName) != 0) {
            // This process is not the daemon
            continue;
        }

        // Kill this daemon process
        int result = killProcess(procList[i].kp_proc.p_pid);
        if (result) {
            numDaemonsKilled++;
        } else {
            error = true;
        }
    }

    // Free the list of processes
    free(procList);

    // If a daemon failed to exit, return DRIVER_FAILURE, otherwise return number of
    // daemons killed
    return error ? DRIVER_FAILURE : numDaemonsKilled;
}

}
