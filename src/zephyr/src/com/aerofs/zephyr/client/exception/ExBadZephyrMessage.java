/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011
 */

package com.aerofs.zephyr.client.exception;

/**
 * TODO: Replace ExBadZephyrMessage with this when server code refactore
 */
public final class ExBadZephyrMessage extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExBadZephyrMessage(String message)
    {
        super(message);
    }
}
