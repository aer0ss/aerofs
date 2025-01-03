/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

public class SFMember {
    public final String email;
    public final String firstName;
    public final String lastName;
    public final String[] permissions;

    public SFMember(String email, String firstName, String lastName,
                    String[] permissions)
    {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.permissions = permissions;
    }
}
