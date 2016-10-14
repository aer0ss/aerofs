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
    public final Collection<SFMember> members;
    // field not introduced until api v1.3
    @Nullable
    public final Collection<SFGroupMember> groups;
    public final Collection<SFPendingMember> pending;
    public final Boolean isExternal;
    // only set in server responses
    @Nullable
    public final String[] callerEffectivePermissions;
    public final Boolean isLocked;

    public SharedFolder(String id, String name, Collection<SFMember> members,
            Collection<SFGroupMember> groupMembers, Collection<SFPendingMember> pending,
            Boolean isExternal, @Nullable String[] callerEffectivePermissions, Boolean isLocked)
    {
        this.id = id;
        this.name = name;
        this.members = members;
        this.groups = groupMembers;
        this.pending = pending;
        this.isExternal = isExternal;
        this.callerEffectivePermissions = callerEffectivePermissions;
        this.isLocked = isLocked;
    }
}
