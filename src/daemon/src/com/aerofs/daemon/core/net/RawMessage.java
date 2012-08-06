package com.aerofs.daemon.core.net;

import java.io.ByteArrayInputStream;

/**
 * A serialized message received from another AeroFS device
 */
public class RawMessage
{
    /**
     * {@link java.io.InputStream} from which the serialized bytes can be read
     */
    public final ByteArrayInputStream _is;

    /**
     * Wire-length of the serialized bytes
     */
    public final int _wirelen;

    public RawMessage(ByteArrayInputStream is, int wirelen)
    {
        _is = is;
        _wirelen = wirelen;
    }
}
