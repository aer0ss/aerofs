/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.audit;

import com.aerofs.ids.SID;

public class AuditFolder
{
    final String id;
    final String name;

    public AuditFolder(SID sid, String name)
    {
        this.id = sid.toStringFormal();
        this.name = name;
    }
}
