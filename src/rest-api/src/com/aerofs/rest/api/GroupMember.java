/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.api;

public class GroupMember
{
    public final String email;
    public final String firstName;
    public final String lastName;

    public GroupMember(String email, String firstName, String lastName)
    {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
