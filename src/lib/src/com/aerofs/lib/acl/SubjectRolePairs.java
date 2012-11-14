/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.acl;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

/**
 * Convenience methods to manipulate collections of {@link com.aerofs.lib.acl.SubjectRolePair}
 */
public final class SubjectRolePairs
{
    public static List<SubjectRolePair> listFromPB(List<PBSubjectRolePair> pbl) throws ExBadArgs
    {
        List<SubjectRolePair> l = Lists.newArrayListWithCapacity(pbl.size());
        for (PBSubjectRolePair pb : pbl) l.add(new SubjectRolePair(pb));
        return l;
    }

    public static List<PBSubjectRolePair> mapToPB(Map<String, Role> subject2role)
    {
        List<PBSubjectRolePair> roles = Lists.newArrayListWithCapacity(subject2role.size());
        for (Map.Entry<String, Role> pair : subject2role.entrySet()) {
            roles.add(new SubjectRolePair(pair.getKey(), pair.getValue()).toPB());
        }
        return roles;
    }
}
