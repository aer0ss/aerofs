package com.aerofs.polaris.api;

public enum TransformType {

    INSERT_CHILD(0),
    RENAME_CHILD(1),
    REMOVE_CHILD(2),
    TOMBSTONE_CHILD(3),
    MAKE_CONTENT(4);

    public final int typeId;

    TransformType(int typeId) {
        this.typeId = typeId;
    }

    public static TransformType fromTypeId(int typeId) throws InvalidTypeException {
        for (TransformType type : TransformType.values()) {
            if (type.typeId == typeId) {
                return type;
            }
        }

        throw new InvalidTypeException(typeId);
    }
}
