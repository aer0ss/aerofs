/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

/**
 * Thrown when the database connection provider cannot provide a valid connection.
 */
public class ExDbInternal extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExDbInternal()
    {
        super();
    }

    public ExDbInternal(Exception e)
    {
        super(e);
    }
}
