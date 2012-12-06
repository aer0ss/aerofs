/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;

import java.util.Collections;
import java.util.List;

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


    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareAndJoinFolder(UserID sharer, SID sid, UserID sharee, Role role)
            throws Exception
    {
        shareFolder(sharer, sid, sharee, role);
        joinSharedFolder(sharer, sid, sharee);
    }

    /**
     * Shares a folder through service.shareFolder with the given user and verifies that an
     * invitation email would've been sent.
     */
    protected void shareFolder(UserID sharer, SID sid, UserID sharee, Role role)
            throws Exception
    {
        sessionUser.set(factUser.create(sharer));
        service.shareFolder(sid.toString(), sid.toPB(), toPB(sharee, role), "").get();
    }

    protected void joinSharedFolder(UserID sharer, SID sid, UserID sharee) throws Exception
    {
        // fopr backward compat with existing tests, accept invite immediately to update ACLs
        sessionUser.set(factUser.create(sharee));
        ListPendingFolderInvitationsReply reply;
        try {
            reply = service.listPendingFolderInvitations().get();
        } catch (ExNotFound e) {
            trans.handleException();
            reply = null;
        }
        if (reply != null) {
            for (PBFolderInvitation inv : reply.getInvitationsList()) {
                if (sharer.toString().equals(inv.getSharer())) {
                    service.joinSharedFolder(inv.getSharedFolderCode());
                }
            }
        }

        sessionUser.set(factUser.create(sharer));
    }
}
