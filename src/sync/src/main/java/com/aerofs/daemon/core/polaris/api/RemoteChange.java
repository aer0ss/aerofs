/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.api;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.ContentHash;

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
    public ContentHash contentHash;
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

    public static RemoteChange updateContent(UniqueID object, ContentHash hash, long size, long mtime)
    {
        RemoteChange rc = new RemoteChange();
        rc.oid = object;
        rc.transformType = Type.UPDATE_CONTENT;
        rc.contentHash = hash;
        rc.contentSize = size;
        rc.contentMtime = mtime;
        rc.originator = DID.generate();
        return rc;
    }
}
