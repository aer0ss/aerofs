/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual_notification;

public final class ExDuplicateNotificationClient extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExDuplicateNotificationClient(String msg)
    {
        super(msg);
    }
}
