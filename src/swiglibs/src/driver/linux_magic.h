// This file contains a bunch of #defines collected from all across the Linux
// kernel sources that give the superblock magic number for various filesystems.

#ifndef CIFS_MAGIC_NUMBER
#define CIFS_MAGIC_NUMBER 0xFF534D42
#endif
#ifndef XFS_SB_MAGIC
#define XFS_SB_MAGIC 0x58465342 /* 'XFSB' */
#endif
#ifndef JFS_SUPER_MAGIC
#define JFS_SUPER_MAGIC 0x3153464a /* "JFS1" */
#endif
#ifndef UFS_MAGIC
#define UFS_MAGIC 0x00011954
#endif
#ifndef UFS2_MAGIC
#define UFS2_MAGIC 0x19540119
#endif
#ifndef HFS_SUPER_MAGIC
#define HFS_SUPER_MAGIC 0x4244
#endif
#ifndef HFSPLUS_SUPER_MAGIC
#define HFSPLUS_SUPER_MAGIC 0x482b
#endif
#ifndef NTFS_SB_MAGIC
#define NTFS_SB_MAGIC 0x5346544e
#endif
#ifndef BTRFS_SUPER_MAGIC
#define BTRFS_SUPER_MAGIC 0x9123683E
#endif
#ifndef ECRYPTFS_SUPER_MAGIC
#define ECRYPTFS_SUPER_MAGIC 0xf15f
#endif
#ifndef FUSE_SUPER_MAGIC
#define FUSE_SUPER_MAGIC 0x65735546
#endif
#ifndef CRAMFS_MAGIC
#define CRAMFS_MAGIC 0x28cd3d45
#endif
#ifndef CRAMFS_MAGIC_WEND
#define CRAMFS_MAGIC_WEND 0x453dcd28
#endif
#ifndef DEBUGFS_MAGIC
#define DEBUGFS_MAGIC 0x64626720
#endif
#ifndef SECURITYFS_MAGIC
#define SECURITYFS_MAGIC 0x73636673
#endif
#ifndef SELINUX_MAGIC
#define SELINUX_MAGIC 0xf97cff8c
#endif
#ifndef RAMFS_MAGIC
#define RAMFS_MAGIC 0x858458f6
#endif
#ifndef TMPFS_MAGIC
#define TMPFS_MAGIC 0x01021994
#endif
#ifndef HUGETLBFS_MAGIC
#define HUGETLBFS_MAGIC 0x958458f6
#endif
#ifndef SQUASHFS_MAGIC
#define SQUASHFS_MAGIC 0x73717368
#endif
#ifndef XENFS_SUPER_MAGIC
#define XENFS_SUPER_MAGIC 0xabba1974
#endif
#ifndef NILFS_SUPER_MAGIC
#define NILFS_SUPER_MAGIC 0x3434
#endif
#ifndef PSTOREFS_MAGIC
#define PSTOREFS_MAGIC 0x6165676C
#endif
#ifndef V9FS_MAGIC
#define V9FS_MAGIC 0x01021997
#endif
#ifndef QNX6_SUPER_MAGIC
#define QNX6_SUPER_MAGIC 0x68191122
#endif
#ifndef BDEVFS_MAGIC
#define BDEVFS_MAGIC 0x62646576
#endif
#ifndef BINFMTFS_MAGIC
#define BINFMTFS_MAGIC 0x42494e4d
#endif
#ifndef PIPEFS_MAGIC
#define PIPEFS_MAGIC 0x50495045
#endif
#ifndef MTD_INODE_FS_MAGIC
#define MTD_INODE_FS_MAGIC 0x11307854
#endif
#ifndef DEVPTS_SUPER_MAGIC
#define DEVPTS_SUPER_MAGIC 0x1cd1
#endif
#ifndef SOCKFS_MAGIC
#define SOCKFS_MAGIC 0x534F434B
#endif
#ifndef SYSFS_MAGIC
#define SYSFS_MAGIC 0x62656572
#endif
