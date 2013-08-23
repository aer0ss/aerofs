/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

/**
 * To be returned with any error code
 */
public class Error
{
    // machine-readable descriptor
    public final String type;
    // human-readable description
    public final String message;

    public Error(String type, String message)
    {
        this.type = type;
        this.message = message;
    }
}
