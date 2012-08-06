/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.ex;

/**
 * Class for all exceptions that are thrown by the transport implementation code. NOTE:
 * this class does not wrap underlying exceptions (for example IOException, etc.) at all. It is
 * used both by itself with a message or as a base class for more specific exceptions.
 */
public class ExTransport extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExTransport(String cause)
    {
        super(cause);
    }
}