/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.api;

public class SFGroupMember {
    public final String id;
    public final String name;
    public final String[] permissions;

    public SFGroupMember(String id, String name, String[] permissions)
    {
        this.id = id;
        this.name = name;
        this.permissions = permissions;
    }
}
