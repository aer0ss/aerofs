/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

import java.util.List;

public class SharedFolder
{
    public final String id;
    public final String name;
    public final List<Member> members;

    public SharedFolder(String id, String name, List<Member> members)
    {
        this.id = id;
        this.name = name;
        this.members = members;
    }
}
