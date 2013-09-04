/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePairs;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.sp.server.lib.user.User;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class AbstractSPFolderTest extends AbstractSPTest
{
    protected static final SID SID_1 = SID.generate();
    protected static final SID SID_2 = SID.generate();

    /**
     * Makes a protobuf-ready list of subject role pairs containing only the given sharee+role pair
     */
    protected static List<PBSubjectRolePair> toPB(User sharee, Role role)
    {
        return SubjectRolePairs.mapToPB(Collections.singletonMap(sharee.id(), role));
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareAndJoinFolder(User sharer, SID sid, User sharee, Role role)
            throws Exception
    {
        assertFalse(sharer.equals(sharee));

        shareFolder(sharer, sid, sharee, role);
        // for backward compat with existing tests, accept invite immediately to update ACLs
        joinSharedFolder(sharee, sid);
        // backward compat
        setSessionUser(sharer);
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     *
     * The folder name is always sid.toStringFormal(). This is required by getSharedFolderCode().
     */
    protected void shareFolder(User sharer, SID sid, User sharee, Role role) throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, role, false);
    }

    protected void shareFolderExternal(User sharer, SID sid, User sharee, Role role)
            throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, role, true);
    }

    private void shareFolderImpl(User sharer, SID sid, User sharee, Role role, boolean ext)
            throws Exception
    {
        setSessionUser(sharer);
        service.shareFolder(sid.toStringFormal(), sid.toPB(), toPB(sharee, role), "", ext);
    }

    protected void joinSharedFolder(User sharee, SID sid) throws Exception
    {
        joinSharedFolderImpl(sharee, sid, false);
    }

    protected void joinSharedFolderExternal(User sharee, SID sid) throws Exception
    {
        joinSharedFolderImpl(sharee, sid, true);
    }

    protected void joinSharedFolderImpl(User sharee, SID sid, boolean ext) throws Exception
    {
        User oldUser = sessionUser.exists() ? sessionUser.get() : null;

        setSessionUser(sharee);
        service.joinSharedFolder(sid.toPB(), ext);

        if (oldUser != null) setSessionUser(oldUser);
    }

    protected void leaveSharedFolder(User sharee, SID sid) throws Exception
    {
        User oldUser = sessionUser.exists() ? sessionUser.get() : null;

        setSessionUser(sharee);
        service.leaveSharedFolder(sid.toPB());

        if (oldUser != null) setSessionUser(oldUser);
    }
}
