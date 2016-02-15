/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import javax.annotation.Nullable;

public enum StorageType
{
    LINKED(S.LINKED_DESCRIPTION),
    LOCAL(S.LOCAL_DESCRIPTION),
    S3(S.S3_DESCRIPTION),
    SWIFT(S.SWIFT_DESCRIPTION);

    private final String _description;

    StorageType(String description)
    {
        _description = description;
    }

    public String description()
    {
        return _description;
    }

    public static StorageType fromOrdinal(int i)
    {
        return values()[i];
    }

    public static @Nullable StorageType fromString(@Nullable String v)
    {
        return v != null ? valueOf(v) : null;
    }

    public boolean isRemote() {
        return (this == S3 || this == SWIFT);
    }
}
