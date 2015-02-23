/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.server.resources;

public final class InvalidCSRException extends Exception
{

    private static final long serialVersionUID = 5932744384279065664L;

    public InvalidCSRException()
    {
        super("could not parse request as a valid Certificate Signing Request (CSR)");
    }

    public InvalidCSRException(String msg)
    {
        super(msg);
    }
}
