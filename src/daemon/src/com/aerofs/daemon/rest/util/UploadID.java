/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.id.UniqueID;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class UploadID
{
    private final @Nullable UniqueID _id;

    public UploadID(String s)
    {
        this(fromStringFormalNullable(s));
    }

    private UploadID(@Nullable UniqueID id)
    {
        _id = id;
    }

    public boolean isValid()
    {
        return _id != null;
    }

    public String toStringFormal()
    {
        return checkNotNull(_id).toStringFormal();
    }

    public static UploadID generate()
    {
        return new UploadID(UniqueID.generate());
    }

    private static @Nullable UniqueID fromStringFormalNullable(String s)
    {
        try {
            return UniqueID.fromStringFormal(s);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public String toString()
    {
        return toStringFormal();
    }
}
