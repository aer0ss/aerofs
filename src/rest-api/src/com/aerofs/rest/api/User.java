/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;


import com.google.common.collect.ImmutableCollection;

public class User
{
    public final String email;
    public final String firstName;
    public final String lastName;

    public final ImmutableCollection<SharedFolder> shares;

    public User(String email, String firstName, String lastName,
            ImmutableCollection<SharedFolder> shares)
    {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.shares = shares;
    }
}
