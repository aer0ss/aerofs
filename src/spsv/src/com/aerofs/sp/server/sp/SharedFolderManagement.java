/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.lib.C;
import com.aerofs.lib.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.InvitationCode.CodeType;
import com.aerofs.servletlib.sp.ISharedFolderDatabase;
import com.aerofs.servletlib.sp.organization.Organization;
import com.aerofs.servletlib.sp.user.User;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.sp.organization.OrganizationManagement;
import com.aerofs.sp.server.sp.user.UserManagement;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

/**
 * Class to handle all interactions with shared folders
 */
public class SharedFolderManagement
{
    private static final Logger l = Util.l(SharedFolderManagement.class);

    ISharedFolderDatabase _db;
    UserManagement _userManagement;
    OrganizationManagement _organizationManagement;
    InvitationEmailer _invitationEmailer;

    public SharedFolderManagement(ISharedFolderDatabase db, UserManagement userManagement,
            OrganizationManagement organizationManagement, InvitationEmailer invitationEmailer)
    {
        _db = db;
        _userManagement = userManagement;
        _organizationManagement = organizationManagement;
        _invitationEmailer = invitationEmailer;
    }

    private void updateSharedFolderName(SID sid, String folderName, String userID)
            throws SQLException
    {
        // Update the folder name only when an owner changes it
        if (_db.getUserPermissionForStore(sid, userID) == Role.OWNER) {
            _db.setFolderName(sid, folderName);
        }
    }

    public void shareFolder(String folderName, SID sid, String userID,
            List<String> emailAddresses, @Nullable String note)
            throws Exception
    {
        User sharer = _userManagement.getUser(userID);

        // Check that the user is verified - only verified users can share
        if (!sharer._isVerified) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm();
        }

        updateSharedFolderName(sid, folderName, userID);

        // TODO could look up sharer Organization once, outside the loop.
        // But if supporting list of email addresses is temporary, don't bother.
        for (String sharee : emailAddresses) {
            shareFolderWithOne(sharer, sharee, folderName, sid, note);
        }
    }

    private void shareFolderWithOne(User sharer, String shareeEmail, String folderName,
            SID sid, @Nullable String note)
            throws Exception
    {
        Organization sharerOrg = _organizationManagement.getOrganization(sharer._orgId);
        Organization shareeOrg;

        User sharee = _userManagement.getUserNullable(shareeEmail);
        if (sharee == null) {  // Sharing with a non-AeroFS user.
            boolean domainMatch = sharerOrg.domainMatches(shareeEmail);
            shareeOrg = _organizationManagement.getOrganization(
                    domainMatch ? sharerOrg._id : C.DEFAULT_ORGANIZATION);
            createFolderInvitation(sharer._id, shareeEmail, sharerOrg, shareeOrg, sid,
                    folderName);
            _userManagement.inviteOneUser(sharer, shareeEmail, shareeOrg, folderName, note);
        } else {
            shareeOrg = _organizationManagement.getOrganization(sharee._orgId);
            String code = createFolderInvitation(sharer._id, sharee._id, sharerOrg, shareeOrg,
                    sid, folderName);
            _invitationEmailer.sendFolderInvitationEmail(sharer._id, sharee._id, sharer._firstName,
                    folderName, note, code);
        }

        l.info("folder sharer " + sharer + " sharee " + shareeEmail);
    }

    /**
     * Creates a folder invitation in the database
     * @return the share folder code
     * @throws ExNoPerm if either the sharer's or sharee's organization does not permit external
     *                  sharing (and they are not the same organization)
     */
    private String createFolderInvitation(String sharerId, String shareeId,
            Organization sharerOrg, Organization shareeOrg, SID sid, String folderName)
            throws ExNoPerm, SQLException, ExNotFound
    {
        if (!sharerOrg.equals(shareeOrg)) {
            if (!sharerOrg._shareExternally) {
                throw new ExNoPerm("Sharing outside this organization is not permitted");
            } else if (!shareeOrg._shareExternally) {
                throw new ExNoPerm("Sharing outside the organization " + shareeOrg
                        + " is not permitted");
            }
        }

        String code = InvitationCode.generate(CodeType.SHARE_FOLDER);

        _db.addShareFolderCode(code, sharerId, shareeId, sid, folderName);

        return code;
    }
}
