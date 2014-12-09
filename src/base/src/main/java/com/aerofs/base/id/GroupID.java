/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.aerofs.base.ex.ExBadArgs;

/**
 * Group ID.
 */
public class GroupID extends IntegerID
{
    public final static GroupID NULL_GROUP = new GroupID(0);
    protected GroupID(int i)
    {
        super(i);
    }

    public static GroupID fromExternal(int i)
            throws ExBadArgs
    {
        if (i == NULL_GROUP.getInt()) {
            throw new ExBadArgs("group ID of " + NULL_GROUP.getInt() + " is reserved");
        }
        return new GroupID(i);
    }

    // encountering the NULL_GROUP id in our own system should never happen, so we throw an
    // IllegalArgumentException
    public static GroupID fromInternal(int i)
    {
        if (i == NULL_GROUP.getInt()) {
            throw new IllegalArgumentException("encountered internal group ID conflicting with null " +
                    "group ID");
        }
        return new GroupID(i);
    }
}
