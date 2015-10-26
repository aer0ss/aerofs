package com.aerofs.polaris.dao;

public enum LockStatus {
    UNLOCKED(0),

    LOCKED(1),

    MIGRATED(2),;

    public final int typeId;

    LockStatus(int typeId) {
        this.typeId = typeId;
    }

    public static LockStatus fromTypeId(int typeId) throws IllegalArgumentException {
        for (LockStatus stat : LockStatus.values()) {
            if (typeId == stat.typeId) {
                return stat;
            }
        }

        throw new IllegalArgumentException(typeId + "is not a valid lock status id");
    }
}
