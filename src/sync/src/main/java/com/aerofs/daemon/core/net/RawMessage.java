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


    public RawMessage(InputStream is)
    {
        _is = is;
    }
}
