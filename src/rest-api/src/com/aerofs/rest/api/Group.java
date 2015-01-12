/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.rest.api;

import java.util.Collection;

public class Group
{
    public final String id;
    public final String name;
    public final Collection<GroupMember> members;

    public Group(String id, String name, Collection<GroupMember> members)
    {
        this.id = id;
        this.name = name;
        this.members = members;
    }
}
