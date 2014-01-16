/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base.id;

import com.aerofs.base.ex.ExFormatError;
import com.google.common.base.Preconditions;

/**
 * A Mobile DID (MDID) is a UUID whose version nibble has been set to 8 instead of the default 4
 */
public class MDID extends DID
{
    private static final int MDID_VERSION_NIBBLE = 8;

    public MDID(UniqueID id)
    {
        super(id);
        Preconditions.checkArgument(getVersionNibble() == MDID_VERSION_NIBBLE);
    }

    public MDID(byte[] bs)
    {
        super(bs);
        Preconditions.checkArgument(getVersionNibble() == MDID_VERSION_NIBBLE);
    }

    public static MDID generate()
    {
        UniqueID uuid = UniqueID.generate();
        setVersionNibble(uuid.getBytes(), MDID_VERSION_NIBBLE);
        return new MDID(uuid);
    }

    public static MDID fromExternal(byte[] bs) throws ExFormatError
    {
        if (bs.length != UniqueID.LENGTH || getVersionNibble(bs) != MDID_VERSION_NIBBLE) {
            throw new ExFormatError();
        }

        return new MDID(bs);
    }

    public static MDID fromInternal(byte[] bs)
    {
        return new MDID(bs);
    }
}
