/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.xray.client.exceptions;

public final class ExHandshakeRenegotiation extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExHandshakeRenegotiation(String message)
    {
        super(message);
    }
}
