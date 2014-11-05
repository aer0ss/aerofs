/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.api;

import com.aerofs.base.NoObfuscation;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;

@NoObfuscation
public class RemoteChange
{
    public enum Type {
        INSERT_CHILD,
        REMOVE_CHILD,
        RENAME_CHILD,
        DELETE_CHILD,
        UPDATE_CONTENT,
    }

    public long logicalTimestamp;
    public UniqueID originator;

    public UniqueID oid;
    public Type transformType;
    public long newVersion;
    public long timestamp;

    // for meta changes
    public OID child;
    public String childName;
    public ObjectType childObjectType;

    // for content changes
    public String contentHash;
    public Long contentSize;
    public Long contentMtime;

    public static RemoteChange insert(UniqueID parent, String name, OID child, ObjectType type)
    {
        RemoteChange rc = new RemoteChange();
        rc.oid = parent;
        rc.transformType = Type.INSERT_CHILD;
        rc.child = child;
        rc.childName = name;
        rc.childObjectType = type;
        return rc;
    }

    public static RemoteChange remove(UniqueID parent, OID child)
    {
        RemoteChange rc = new RemoteChange();
        rc.oid = parent;
        rc.transformType = Type.REMOVE_CHILD;
        rc.child = child;
        return rc;
    }

    public static RemoteChange rename(UniqueID parent, String name, OID child)
    {
        RemoteChange rc = new RemoteChange();
        rc.oid = parent;
        rc.transformType = Type.RENAME_CHILD;
        rc.child = child;
        rc.childName = name;
        return rc;
    }
}