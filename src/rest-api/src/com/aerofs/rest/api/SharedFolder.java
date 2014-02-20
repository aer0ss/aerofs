/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

import java.util.Collection;

public class SharedFolder
{
    public final String id;
    public final String name;
    public final Collection<Member> members;
    public final Collection<PendingMember> pending;

    public SharedFolder(String id, String name, Collection<Member> members,
            Collection<PendingMember> pending)
    {
        this.id = id;
        this.name = name;
        this.members = members;
        this.pending = pending;
    }
}
