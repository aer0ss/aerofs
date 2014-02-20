/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;


import javax.annotation.Nullable;

public class PendingMember
{
    public final String email;
    public final String firstName;
    public final String lastName;
    public final String invitedBy;
    public final String[] permissions;

    // write-only: may only be non-null when incoming
    public final @Nullable String note;

    public PendingMember(String email, String firstName, String lastName, String invitedBy,
            String[] permissions)
    {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.invitedBy = invitedBy;
        this.permissions = permissions;
        this.note = null;
    }

    public PendingMember(String email, String[] permissions, String note)
    {
        this.email = email;
        this.firstName = null;
        this.lastName = null;
        this.invitedBy = null;
        this.permissions = permissions;
        this.note = note;
    }
}
