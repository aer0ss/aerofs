/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

import javax.annotation.Nullable;
import java.util.Collection;

public class SharedFolder
{
    public final String id;
    public final String name;
    public final Collection<Member> members;
    public final Collection<PendingMember> pending;
    public final Boolean isExternal;
    // only set in server responses
    @Nullable
    public final String[] callerEffectivePermissions;

    public SharedFolder(String id, String name, Collection<Member> members,
            Collection<PendingMember> pending, Boolean isExternal, @Nullable String[] callerEffectivePermissions)
    {
        this.id = id;
        this.name = name;
        this.members = members;
        this.pending = pending;
        this.isExternal = isExternal;
        this.callerEffectivePermissions = callerEffectivePermissions;
    }
}
