/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.ISharedFolderDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Class to handle all interactions with shared folders
 */
public class SharedFolderManagement
{
    private static final Logger l = Util.l(SharedFolderManagement.class);

    ISharedFolderDatabase _db;
    UserManagement _userManagement;
    OrganizationManagement _organizationManagement;
    InvitationEmailer.Factory _emailerFactory;

    public SharedFolderManagement(ISharedFolderDatabase db, UserManagement userManagement,
            OrganizationManagement organizationManagement, InvitationEmailer.Factory emailerFactory)
    {
        _db = db;
        _userManagement = userManagement;
        _organizationManagement = organizationManagement;
        _emailerFactory = emailerFactory;
    }

    private void updateSharedFolderName(SID sid, String folderName, String userID)
            throws SQLException
    {
        // Update the folder name only when an owner changes it
        if (_db.getUserPermissionForStore(sid, userID) == Role.OWNER) {
            _db.setFolderName(sid, folderName);
        }
    }

    /**
     * Shares the given folder with the given set of subject-role pairs.
     * @param emails invitation emails to be sent once the transaction is committed (out arg)
     * @return a map of user IDs and epochs to be returned via verkehr.
     */
    public Map<String, Long> shareFolder(String folderName, SID sid, String userID,
            List<SubjectRolePair> rolePairs, @Nullable String note, List<InvitationEmailer> emails)
            throws Exception
    {
        User sharer = _userManagement.getUser(userID);

        // Check that the user is verified - only verified users can share
        if (!sharer._isVerified) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm("user " + userID + " is not yet verified");
        }

        // Move forward with setting ACLs and sharing the folder
        // NB: if any user fails business rules check, the whole transaction is rolled back
        Map<String, Long> epochs = _db.createACL(userID, sid, rolePairs);
        updateSharedFolderName(sid, folderName, userID);

        for (SubjectRolePair rolePair : rolePairs) {
            emails.add(shareFolderWithOne(sharer, rolePair._subject, folderName, sid, note));
        }

        return epochs;
    }

    private InvitationEmailer shareFolderWithOne(final User sharer, String shareeEmail,
            final String folderName, SID sid, @Nullable final String note)
            throws Exception
    {
        InvitationEmailer emailer;
        Organization shareeOrg;
        final User sharee = _userManagement.getUserNullable(shareeEmail);
        if (sharee == null) {  // Sharing with a non-AeroFS user.
            shareeOrg = _organizationManagement.getOrganization(OrgID.DEFAULT);
            createFolderInvitation(sharer._id, shareeEmail, sid, folderName);
            emailer = _userManagement.inviteOneUser(sharer, shareeEmail, shareeOrg, folderName,
                    note);
        } else if (!sharee._id.equals(sharer._id)) {
            final String code = createFolderInvitation(sharer._id, sharee._id, sid, folderName);
            emailer = _emailerFactory.createFolderInvitation(sharer._id, sharee._id,
                            sharer._firstName, folderName, note, code);
        } else {
            // do not create invite code for/send email to the requester
            l.warn(sharer + " tried to invite himself");
            emailer = new InvitationEmailer();
        }

        l.info("folder sharer " + sharer + " sharee " + shareeEmail);
        return emailer;
    }

    /**
     * Creates a folder invitation in the database
     * @return the share folder code
     */
    private String createFolderInvitation(String sharerId, String shareeId, SID sid,
            String folderName)
            throws SQLException
    {
        // TODO check BOTH sharer and sharee's organization allow this cross-organization sharing.

        String code = InvitationCode.generate(CodeType.SHARE_FOLDER);

        _db.addShareFolderCode(code, sharerId, shareeId, sid, folderName);

        return code;
    }
}
