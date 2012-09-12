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
#include <linux/magic.h>
#include "linux_magic.h"
#include <errno.h>

// See statfs(2), linux_magic.h, and /usr/include/linux/magic.h for information
// on these constants.  We place some of the more common filesystems at the top
// for performance, since we do a linear search.
static struct fsid {
    __SWORD_TYPE magic;
    const char * fs_name;
} fstypes[] = {
    { EXT2_SUPER_MAGIC,        "ext2"},  // Note: all the ext fses have the same magic
    { BTRFS_SUPER_MAGIC,       "btrfs"},
    { XFS_SB_MAGIC,            "xfs"},
    { REISERFS_SUPER_MAGIC,    "reiserfs"},
    { JFS_SUPER_MAGIC,         "jfs"},
    { ECRYPTFS_SUPER_MAGIC,    "ecryptfs"},
    { NFS_SUPER_MAGIC,         "nfs"},
    { MSDOS_SUPER_MAGIC,       "msdos"},
    { FUSE_SUPER_MAGIC,        "fuse"}, // Fuse is used for ntfs3g, among others
    // Less common or silly things below, added because I'd rather do this once than
    // have to come back and do it again when someone decides to do something silly
    // like put AeroFS in a ramdisk
    { ADFS_SUPER_MAGIC,        "adfs"},
    { AFFS_SUPER_MAGIC,        "affs"},
    { AFS_SUPER_MAGIC,         "afs"},
    { AUTOFS_SUPER_MAGIC,      "autofs"},
    { CIFS_MAGIC_NUMBER,       "cifs"},
    { CODA_SUPER_MAGIC,        "coda"},
    { CRAMFS_MAGIC,            "cramfs"},
    { CRAMFS_MAGIC_WEND,       "cramfswend"}, // CRAMFS_MAGIC, with wrong endianness.
    { DEBUGFS_MAGIC,           "debugfs"},
    { HFS_SUPER_MAGIC,         "hfs"},
    { SECURITYFS_MAGIC,        "securityfs"},
    { SELINUX_MAGIC,           "selinux"},
    { RAMFS_MAGIC,             "ramfs"},
    { TMPFS_MAGIC,             "tmpfs"},
    { HUGETLBFS_MAGIC,         "hugetlbfs"},
    { SQUASHFS_MAGIC,          "squashfs"},
    { EFS_SUPER_MAGIC,         "efs"},
    { XENFS_SUPER_MAGIC,       "xenfs"},
    { NILFS_SUPER_MAGIC,       "nilfs"},
    { HPFS_SUPER_MAGIC,        "hpfs"},
    { ISOFS_SUPER_MAGIC,       "isofs"},
    { JFFS2_SUPER_MAGIC,       "jffs"},
    { PSTOREFS_MAGIC,          "pstorefs"},
    { MINIX_SUPER_MAGIC,       "minix14"},   /* minix v1 fs, 14 char names */
    { MINIX_SUPER_MAGIC2,      "minix30"},   /* minix v1 fs, 30 char names */
    { MINIX2_SUPER_MAGIC,      "minixv214"}, /* minix v2 fs, 14 char names */
    { MINIX2_SUPER_MAGIC2,     "minixv230"}, /* minix v2 fs, 30 char names */
    { MINIX3_SUPER_MAGIC,      "minix3"},
    { NCP_SUPER_MAGIC,         "ncp"},
    { OPENPROM_SUPER_MAGIC,    "openprom"},
    { QNX4_SUPER_MAGIC,        "qnx4"},
    { QNX6_SUPER_MAGIC,        "qnx6"},
    { SMB_SUPER_MAGIC,         "smb"},
    { CGROUP_SUPER_MAGIC,      "cgroup"},
    { V9FS_MAGIC,              "v9fs"},
    { BDEVFS_MAGIC,            "bdevfs"},
    { BINFMTFS_MAGIC,          "binfmtfs"},
    { DEVPTS_SUPER_MAGIC,      "devfs"},
    { FUTEXFS_SUPER_MAGIC,     "futexfs"},
    { PIPEFS_MAGIC,            "pipefs"},
    { PROC_SUPER_MAGIC,        "procfs"},
    { SOCKFS_MAGIC,            "sockfs"},
    { SYSFS_MAGIC,             "sysfs"},
    { USBDEVICE_SUPER_MAGIC,   "usbfs"},
    { MTD_INODE_FS_MAGIC,      "mtdinode"},
    { ANON_INODE_FS_MAGIC,     "anoninode"},
    { 0, NULL }
};

// Things we actually care about supporting:
// "EXT", "NFS", "BTRFS", "ECRYPTFS", "REISERFS", "ROOTFS", "XFS", "UFS", "CRYPT", "JFS", "SIMFS" };

namespace Driver {

void setFolderIcon(JNIEnv* env, jstring folderPath, jstring iconName)
{
    // TODO: implement me
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
    // Now we see which of the magic numbers this path has for f_type
    struct fsid *this_fsid = fstypes;
    // The last item in the list has NULL for its filename.
    while (this_fsid->fs_name) {
        // Check if the file's backing fs magic number matches
        if (stfs.f_type == this_fsid->magic) {
            // Found it!
            // Check that the name of the filesystem fits in the buffer; if
            // not, return -ENOSPC
            if (strlen(this_fsid->fs_name) + 1 > buflen) {
                return -ENOSPC;
            }
            // Copy the filesystem name into buffer and return whether it was
            // remote or local
            strcpy((char*)buffer, this_fsid->fs_name);
            if (this_fsid->magic == NFS_SUPER_MAGIC || this_fsid->magic == CIFS_MAGIC_NUMBER ) {
                return FS_REMOTE;
            } else {
                return FS_LOCAL;
            }
        }
        // Try the next magic/fs_name in the list
        this_fsid++;
    }
    // The filesystem type for the file given didn't map to any known fs magic.
    // Log the weird value, and return failure.
    FERROR("f_type: " << stfs.f_type);
    return -ENOENT;
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

int killDaemon()
{
    // The 3 is for NULL character and path separators
    char pathToCmdline[sizeof(PROC_PATH) + sizeof(CMDLINE_FILE) + NAME_MAX + 3];

    char cmdlineNameBuffer[MAX_CMDLINE_LEN + 1];

    // Open the /proc directory
    DIR* procDir = opendir(PROC_PATH);
    if (procDir == NULL) {
        FERROR(" no /proc on filesystem");
        return -1;
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

        // Allocate the buffer and scan the first argument
        // Be careful not to overflow the buffer
        fscanf(fCmdline, "%" STRINGIFY(MAX_CMDLINE_LEN) "s", cmdlineNameBuffer);

        // Close the file, we're done with it
        fclose(fCmdline);

        // Extract the process name from the cmdline argument path
        char* processName = basename(cmdlineNameBuffer);

        // Check to see if this process is 'aerofsd'
        if (strcmp(processName, DAEMON_PROC_NAME) == 0) {
            // This is the aerofs daemon, add it to the vector
            // of aerofs daemon pids so far encountered
            daemonProcs.push_back((pid_t)atoi(dirEntity->d_name));
        }
    }

    // Finished with "/proc"
    closedir(procDir);

    bool error = false;

    // Kill all the discovered daemon processes
    std::vector<pid_t>::iterator iter = daemonProcs.begin();
    for (; iter != daemonProcs.end(); iter++) {
        FINFO(" kill " << *iter);

        // Kill it!! Kill them all!!
        error |= !killProcess(*iter);
    }

    // Return number of daemons killed if successful, otherwise DRIVER_FAILURE
    return error ? DRIVER_FAILURE : daemonProcs.size();
}

}
