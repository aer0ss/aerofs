/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.acl;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Convenience methods to manipulate collections of {@link SubjectPermissions}
 */
public final class SubjectPermissionsList
{
    public static List<SubjectPermissions> listFromPB(List<PBSubjectPermissions> pbl)
            throws ExBadArgs
    {
        List<SubjectPermissions> l = Lists.newArrayListWithCapacity(pbl.size());
        for (PBSubjectPermissions pb : pbl) l.add(SubjectPermissions.fromPB(pb));
        return l;
    }

    public static List<PBSubjectPermissions> mapToPB(Map<String, Permissions> subject2role)
    {
        return subject2role.entrySet().stream()
                .map(entry -> PBSubjectPermissions.newBuilder()
                        .setSubject(entry.getKey())
                        .setPermissions(entry.getValue().toPB())
                        .build())
                .collect(toList());
    }
}
