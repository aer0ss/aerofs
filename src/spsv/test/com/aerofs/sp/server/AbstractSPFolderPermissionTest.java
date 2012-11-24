/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class AbstractSPFolderPermissionTest extends AbstractSPUserBasedTest
{
    protected static final SID TEST_SID_1 = new SID(UniqueID.generate());
    protected static final SID TEST_SID_2 = new SID(UniqueID.generate());

    /**
     * Makes a protobuf-ready list of subject role pairs containing only the given sharee+role pair
     */
    protected static List<PBSubjectRolePair> makePair(UserID sharee, Role role)
    {
        return ImmutableList.of(new SubjectRolePair(sharee, role).toPB());
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareFolderThroughSP(UserID sharer, SID sid, UserID sharee, Role role)
            throws Exception
    {
        sessionUser.setID(sharer);
        List<PBSubjectRolePair> pair = makePair(sharee, role);
        service.shareFolder(sid.toString(), sid.toPB(), pair, "").get();
    }
}
