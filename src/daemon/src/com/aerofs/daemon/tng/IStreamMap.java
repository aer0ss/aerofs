/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;

import javax.annotation.Nullable;

/**
 * A map holding StreamID -> IImmutableStream subclass associations.
 */
public interface IStreamMap<Stream extends IStream>
{
    void add(Stream stream)
            throws ExStreamAlreadyExists;

    Stream get(StreamID streamid)
            throws ExStreamInvalid;

    @Nullable
    Stream remove(StreamID strmid);
}
