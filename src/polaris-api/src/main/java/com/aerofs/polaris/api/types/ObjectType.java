package com.aerofs.polaris.api.types;

/**
 * List of logical objects that can be represented in the AeroFS virtual filesystem. These are:
 * <ul>
 *     <li>STORE</li>
 *     <li>FILE</li>
 *     <li>FOLDER</li>
 *     <li>MOUNT_POINT (shared-folder mount point)</li>
 * </ul>
 *
 * <strong>IMPORTANT:</strong> The type ids start at 1 because the Java ResultSet API
 * is "suboptimal" and returns 0 if the value in the column is null. Yeah. Really.
 */
public enum ObjectType {

    /** shared folder */
    STORE(1),

    /** file on the filesystem */
    FILE(2),

    /** folder on the filesystem */
    FOLDER(3),

    /** mount point for a shared folder */
    MOUNT_POINT(4);

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
