package com.aerofs.daemon.transport.presence;

/**
 * Raised when a PresenceLocation is invalid
 */
public class ExInvalidPresenceLocation extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExInvalidPresenceLocation(String s) { super(s); }
    public ExInvalidPresenceLocation(String s, Throwable throwable) { super(s, throwable); }
}
