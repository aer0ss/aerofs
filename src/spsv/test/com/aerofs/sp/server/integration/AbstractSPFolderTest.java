/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.ids.SID;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;
import com.aerofs.sp.server.lib.user.User;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertFalse;

public class AbstractSPFolderTest extends AbstractSPTest
{
    protected static final SID SID_1 = SID.generate();
    protected static final SID SID_2 = SID.generate();

    /**
     * Makes a protobuf-ready list of subject role pairs containing only the given sharee+role pair
     */
    protected static List<PBSubjectPermissions> toPB(User sharee, Permissions permissions)
    {
        return newArrayList(new SubjectPermissions(sharee.id(), permissions).toPB());
    }

    protected static List<PBSubjectPermissions> toPB(Group sharee, Permissions permissions)
    {
        return newArrayList(new SubjectPermissions(sharee.id(), permissions).toPB());
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareAndJoinFolder(User sharer, SID sid, User sharee, Permissions permissions)
            throws Exception
    {
        assertFalse(sharer.equals(sharee));

        shareFolder(sharer, sid, sharee, permissions);
        // for backward compat with existing tests, accept invite immediately to update ACLs
        joinSharedFolder(sharee, sid);
        // backward compat
        setSession(sharer);
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     *
     * The folder name is always sid.toStringFormal(). This is required by getSharedFolderCode().
     */
    protected void shareFolder(User sharer, SID sid, User sharee, Permissions permissions) throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, permissions, false, false);
    }

    protected void shareFolder(User sharer, SID sid, Group sharee, Permissions permissions) throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, permissions, false, false);
    }

    protected void shareFolderSuppressWarnings(User sharer, SID sid, User sharee, Permissions permissions)
            throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, permissions, false, true);
    }

    protected void shareFolderSuppressWarnings(User sharer, SID sid, Group sharee, Permissions permissions)
            throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, permissions, false, true);
    }

    protected void shareFolderExternal(User sharer, SID sid, User sharee, Permissions permissions)
            throws Exception
    {
        shareFolderImpl(sharer, sid, sharee, permissions, true, false);
    }

    private void shareFolderImpl(User sharer, SID sid, User sharee, Permissions permissions, boolean external,
            boolean suppressWarnings)
            throws Exception
    {
        setSession(sharer);
        service.shareFolder(sid.toStringFormal(), BaseUtil.toPB(sid), toPB(sharee, permissions), "", external,
                suppressWarnings);
    }

    private void shareFolderImpl(User sharer, SID sid, Group sharee, Permissions permissions, boolean external,
            boolean suppressWarnings)
            throws Exception
    {
        setSession(sharer);
        service.shareFolder(sid.toStringFormal(), BaseUtil.toPB(sid), toPB(sharee, permissions), "", external,
                suppressWarnings);
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
        User oldUser = session.isAuthenticated() ?
                session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY) :
                null;

        setSession(sharee);
        service.joinSharedFolder(BaseUtil.toPB(sid), ext);

        if (oldUser != null) setSession(oldUser);
    }

    protected void leaveSharedFolder(User sharee, SID sid) throws Exception
    {
        User oldUser = session.isAuthenticated() ?
                session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY) :
                null;

        setSession(sharee);
        service.leaveSharedFolder(BaseUtil.toPB(sid));

        if (oldUser != null) setSession(oldUser);
    }
}
