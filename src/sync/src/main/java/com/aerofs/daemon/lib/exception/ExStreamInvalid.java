package com.aerofs.daemon.lib.exception;

import com.aerofs.proto.Transport.PBStream.InvalidationReason;

import java.io.IOException;

/**
 * Thrown when a stream is invalidated (either by a remote peer's action, or because of
 * a local one). It is called ExStreamInvalid as opposed to ExInvalidStream because this
 * allows us to search for all stream-related exceptions easily, simply by typing "ExStream..."
 */
public class ExStreamInvalid extends IOException
{
    private static final long serialVersionUID = 1L;
    private final InvalidationReason _reason;

    public ExStreamInvalid(InvalidationReason reason)
    {
        _reason = reason;
    }

    public InvalidationReason getReason()
    {
        return _reason;
    }

    @Override
    public String toString()
    {
        return "stream invalid: " + _reason;
    }
}
