/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client.exception;

public final class ExHandshakeRenegotiation extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExHandshakeRenegotiation(String message)
    {
        super(message);
    }
}
