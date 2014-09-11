package com.aerofs.polaris.api;

/**
 * List of logical objects that can be represented in the AeroFS virtual filesystem. These are:
 * <ul>
 *     <li>ROOT</li>
 *     <li>FILE</li>
 *     <li>FOLDER</li>
 *     <li>MOUNT_POINT (shared-folder mount point)</li>
 * </ul>
 */
public enum ObjectType {

    /** root of a shared folder */
    ROOT(0),

    /** file on the filesystem */
    FILE(1),

    /** folder on the filesystem */
    FOLDER(2),

    /** mount point for a shared folder */
    MOUNT_POINT(3);

    public final int typeId;

    ObjectType(int typeId) {
        this.typeId = typeId;
    }

    public static ObjectType fromTypeId(int typeId) throws IllegalArgumentException {
        for (ObjectType type : ObjectType.values()) {
            if (type.typeId == typeId) {
                return type;
            }
        }

        throw new IllegalArgumentException(typeId + "is not a valid object type id");
    }
}
