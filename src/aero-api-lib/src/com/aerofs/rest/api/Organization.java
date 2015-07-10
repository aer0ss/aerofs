/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;


public class Organization
{
    public final String id;
    public final String name;
    public final Long quota;

    public Organization(String id, String name, Long quota)
    {
        this.id = id;
        this.name = name;
        this.quota = quota;
    }
}
