package com.aerofs.polaris.api.types;

/**
 * <strong>IMPORTANT:</strong> The type ids start at 1 because the Java ResultSet API
 * is "suboptimal" and returns 0 if the value in the column is null. Yeah. Really.
 */
public enum TransformType {

    /** define a parent-child relationship between two objects */
    INSERT_CHILD(1),

    /** remove a parent-child relationship between two objects */
    REMOVE_CHILD(2),

    /** rename the child to object id mapping */
    RENAME_CHILD(3),

    /** delete an object permanently */
    DELETE_CHILD(4),

    /** change the content of a file */
    UPDATE_CONTENT(5);

    public final int typeId;

    TransformType(int typeId) {
        this.typeId = typeId;
    }

    public static TransformType fromTypeId(int typeId) throws IllegalArgumentException {
        for (TransformType type : TransformType.values()) {
            if (typeId == type.typeId) {
                return type;
            }
        }

        throw new IllegalArgumentException(typeId + "is not a valid transform type id");
    }
}
