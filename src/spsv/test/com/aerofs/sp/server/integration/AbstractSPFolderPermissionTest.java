/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.sp.server.lib.user.User;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class AbstractSPFolderPermissionTest extends AbstractSPTest
{
    protected static final SID TEST_SID_1 = SID.generate();
    protected static final SID TEST_SID_2 = SID.generate();

    /**
     * Makes a protobuf-ready list of subject role pairs containing only the given sharee+role pair
     */
    protected static List<PBSubjectRolePair> toPB(UserID sharee, Role role)
    {
        return SubjectRolePairs.mapToPB(Collections.singletonMap(sharee, role));
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareAndJoinFolder(UserID sharer, SID sid, UserID sharee, Role role)
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
    protected void shareFolder(UserID sharer, SID sid, UserID sharee, Role role)
            throws Exception
    {
        setSessionUser(sharer);
        service.shareFolder(sid.toStringFormal(), sid.toPB(), toPB(sharee, role), "");
    }

    protected void joinSharedFolder(UserID sharee, SID sid) throws Exception
    {
        User oldUser = sessionUser.exists() ? sessionUser.get() : null;

        setSessionUser(sharee);
        service.joinSharedFolder(sid.toPB());

        if (oldUser != null) setSessionUser(oldUser.id());
    }

    protected void leaveSharedFolder(UserID sharee, SID sid) throws Exception
    {
        User oldUser = sessionUser.exists() ? sessionUser.get() : null;

        setSessionUser(sharee);
        service.leaveSharedFolder(sid.toPB());

        if (oldUser != null) setSessionUser(oldUser.id());
    }
}
