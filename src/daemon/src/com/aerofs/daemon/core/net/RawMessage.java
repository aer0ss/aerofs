package com.aerofs.daemon.core.net;

import java.io.InputStream;

/**
 * A serialized message received from another AeroFS device
 */
public class RawMessage
{
    /**
     * {@link java.io.InputStream} from which the serialized bytes can be read
     */
    public final InputStream _is;

    /**
     * Wire-length of the serialized bytes
     */
    public final int _wirelen;

    public RawMessage(InputStream is, int wirelen)
    {
        _is = is;
        _wirelen = wirelen;
    }
}
