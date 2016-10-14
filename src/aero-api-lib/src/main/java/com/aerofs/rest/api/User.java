/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;


import java.util.Collection;

public class User
{
    public final String email;
    public final String firstName;
    public final String lastName;

    public final Collection<SharedFolder> shares;
    public final Collection<Invitation> invitations;

    public User(String email, String firstName, String lastName,
            Collection<SharedFolder> shares,
            Collection<Invitation> invitations)
    {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.shares = shares;
        this.invitations = invitations;
    }
}
