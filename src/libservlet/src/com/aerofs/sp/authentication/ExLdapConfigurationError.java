/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

/**
 * This may be thrown to indicate a blocking server configuration error.
 */
public class ExLdapConfigurationError extends Error
{
    private static final long serialVersionUID = 3727044639940716290L;

    public ExLdapConfigurationError() { super("Error detected in server configuration"); }
    public ExLdapConfigurationError(String message) { super(message); }
}
