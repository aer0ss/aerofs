/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

public class Invitation
{
    public final String shareId;
    public final String shareName;
    public final String invitedBy;
    public final String[] permissions;

    public Invitation(String shareId, String shareName, String invitedBy, String[] permissions)
    {
        this.shareId = shareId;
        this.shareName = shareName;
        this.invitedBy = invitedBy;
        this.permissions = permissions;
    }
}
