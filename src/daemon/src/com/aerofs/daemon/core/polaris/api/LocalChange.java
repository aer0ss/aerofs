/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.api;

public class LocalChange
{
    public enum Type {
        INSERT_CHILD,
        MOVE_CHILD,
        REMOVE_CHILD,
        UPDATE_CONTENT
    }

    public Type type;

    // insert, move, remove
    public String child;

    // insert
    public String childName;
    public ObjectType childObjectType;

    // move
    public String newParent;
    public String newChildName;

    // update
    public Long localVersion;
    public String contentHash;
    public Long contentSize;
    public Long contentMtime;
}
