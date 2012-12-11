/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.Util;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class AbstractSPFolderPermissionTest extends AbstractSPTest
{
    protected static final SID TEST_SID_1 = new SID(UniqueID.generate());
    protected static final SID TEST_SID_2 = new SID(UniqueID.generate());

    /**
     * Makes a protobuf-ready list of subject role pairs containing only the given sharee+role pair
     */
    protected static List<PBSubjectRolePair> toPB(UserID sharee, Role role)
    {
        return SubjectRolePairs.mapToPB(Collections.singletonMap(sharee, role));
    }


    protected Set<String> mockVerkehrToSuccessfullyPublishAndStoreSubscribers()
    {
        final Set<String> published = new HashSet<String>();
        when(verkehrPublisher.publish_(any(String.class), any(byte[].class)))
                .then(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        published.add((String)invocation.getArguments()[0]);
                        return UncancellableFuture.createSucceeded(null);
                    }
                });
        return published;
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
        joinSharedFolder(sharer, sid, sharee);
        // backward compat
        sessionUser.set(factUser.create(sharer));
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareFolder(UserID sharer, SID sid, UserID sharee, Role role)
            throws Exception
    {
        sessionUser.set(factUser.create(sharer));
        service.shareFolder(sid.toStringFormal(), sid.toPB(), toPB(sharee, role), "")
                .get();
    }

    protected @Nullable String getSharedFolderCode(UserID sharer, SID sid, UserID sharee)
            throws Exception
    {
        sessionUser.set(factUser.create(sharee));
        ListPendingFolderInvitationsReply reply = service.listPendingFolderInvitations().get();

        for (PBFolderInvitation inv : reply.getInvitationsList()) {
            if (sharer.toString().equals(inv.getSharer()) &&
                    sid.toStringFormal().equals(inv.getFolderName())) {
                return inv.getSharedFolderCode();
            }
        }

        return null;
    }

    protected void joinSharedFolder(UserID sharer, SID sid, UserID sharee) throws Exception
    {
        String code = getSharedFolderCode(sharer, sid, sharee);
        assertNotNull(code);
        joinSharedFolder(sharee, code);
    }

    protected void joinSharedFolder(UserID sharee, String code) throws Exception
    {
        sessionUser.set(factUser.create(sharee));
        service.joinSharedFolder(code);
    }
}
