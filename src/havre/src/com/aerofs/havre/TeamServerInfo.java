/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.havre;

import com.aerofs.base.id.UserID;
import com.google.common.base.Joiner;

import java.util.List;

public class TeamServerInfo extends Version
{
    public final List<UserID> users;

    public TeamServerInfo(int major, int minor, List<UserID> users)
    {
        super(major, minor);
        this.users = users;
    }

    @Override
    public String toString()
    {
        return "{" + super.toString() + ", [" + Joiner.on(",").join(users.toArray()) + "]}";
    }
}