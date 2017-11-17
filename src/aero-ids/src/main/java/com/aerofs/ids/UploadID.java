/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.ids;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class UploadID
{
    private final @Nullable DID _did;
    private final @Nullable UniqueID _id;

    public UploadID(String s)
    {
        this(fromStringFormalNullable(s));
    }

    private UploadID(UploadID uid) {
        this(uid._did, uid._id);
    }

    private UploadID(@Nullable DID did, @Nullable UniqueID id)
    {
        _did = did;
        _id = id;
    }

    public static boolean isValid(UploadID id)
    {
        return id != null && id._id != null;
    }

    public @Nullable DID did() {
        return _did;
    }

    public String prefixId() {
        return checkNotNull(_id).toStringFormal();
    }

    public String toStringFormal()
    {
        checkNotNull(_id);
        if (_did == null) return _id.toStringFormal();
        StringBuilder bd = new StringBuilder();
        bd.append(_did.toStringFormal());
        bd.append(_id.toStringFormal());
        return bd.toString();
    }

    public static UploadID generate()
    {
        return generate(null);
    }

    public static UploadID generate(DID did)
    {
        return new UploadID(did, UniqueID.generate());
    }

    public static @Nullable UploadID fromStringFormalNullable(String s)
    {
        try {
            if (s.length() == 32) {
                return new UploadID(null, UniqueID.fromStringFormal(s));
            } else if (s.length() == 64) {
                return new UploadID(new DID(s, 0, 32), new UniqueID(s, 32, 64));
            }
            return null;
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
