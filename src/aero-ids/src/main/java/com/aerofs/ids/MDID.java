/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.ids;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A Mobile DID (MDID) is a UUID whose version nibble has been set to 8 instead of the default 4
 */
public class MDID extends DID
{
    protected MDID(UniqueID id)
    {
        super(id);
        checkArgument(isMobileDevice());
    }

    protected MDID(byte[] bs)
    {
        super(bs);
        checkArgument(isMobileDevice());
    }

    public static MDID generate()
    {
        UniqueID uuid = UniqueID.generate();
        setVersionNibble(uuid.getBytes(), MDID_VERSION_NIBBLE);
        return new MDID(uuid);
    }

    public static MDID fromExternal(byte[] bs) throws ExInvalidID
    {
        if (bs.length != UniqueID.LENGTH || getVersionNibble(bs) != MDID_VERSION_NIBBLE) {
            throw new ExInvalidID();
        }
        return new MDID(bs);
    }

    public static MDID fromInternal(byte[] bs)
    {
        return new MDID(bs);
    }
}
