package com.aerofs.polaris.api;

public enum TransformType {

    /** define a parent-child relationship between two objects */
    INSERT_CHILD(0),

    /** remove a parent-child relationship between two objects */
    REMOVE_CHILD(1),

    /** rename the child to object id mapping */
    RENAME_CHILD(2),

    /** delete an object permanently */
    DELETE_CHILD(3),

    /** change the content of a file */
    UPDATE_CONTENT(4);

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
