/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

/**
 * Group ID.
 */
public class GroupID extends IntegerID
{
    public final static GroupID NULL_GROUP = new GroupID(0);
    public GroupID(int i)
    {
        super(i);
    }
}
