/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.acl;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

/**
 * Convenience methods to manipulate collections of {@link SubjectPermissions}
 */
public final class SubjectPermissionsList
{
    public static List<SubjectPermissions> listFromPB(List<PBSubjectPermissions> pbl)
            throws ExBadArgs, ExEmptyEmailAddress
    {
        List<SubjectPermissions> l = Lists.newArrayListWithCapacity(pbl.size());
        for (PBSubjectPermissions pb : pbl) l.add(SubjectPermissions.fromPB(pb));
        return l;
    }

    public static List<PBSubjectPermissions> mapToPB(Map<UserID, Permissions> subject2role)
    {
        List<PBSubjectPermissions> roles = Lists.newArrayListWithCapacity(subject2role.size());
        for (Map.Entry<UserID, Permissions> pair : subject2role.entrySet()) {
            roles.add(new SubjectPermissions(pair.getKey(), pair.getValue()).toPB());
        }
        return roles;
    }
}
