/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

/**
 * To be returned with any error code
 */
public class Error
{
    public enum Type
    {
        BAD_ARGS,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        INTERNAL_ERROR,
        TOO_MANY_REQUESTS,
        INSUFFICIENT_STORAGE,
        NOT_IMPLEMENTED
    }

    // machine-readable descriptor
    public final String type;
    // human-readable description
    public final String message;

    public Error(Type type, String message)
    {
        this.type = type.toString();
        this.message = message;
    }
}
