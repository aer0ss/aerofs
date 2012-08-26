/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.spsv.sendgrid;

public class Sendgrid
{
    public static enum Events
    {
        PROCESSED,
        DROPPED,
        DELIVERED,
        DEFERRED,
        BOUNCE,
        OPEN,
        CLICK,
        SPAMREPORT,
        UNSUBSCRIBE
    }

    public static enum Category
    {
        //NOTE: new categories must be appended at the end, or the ORDINAL values may be thrown off
        FOLDERLESS_INVITE,
        FOLDER_INVITE,
        PASSWORD_RESET,
        SUPPORT
    }
}