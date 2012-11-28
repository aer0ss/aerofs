/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.ISharedFolderDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.user.UserManagement;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to handle all interactions with shared folders
 */
public class SharedFolderManagement
{
    private static final Logger l = Util.l(SharedFolderManagement.class);

    private final ISharedFolderDatabase _db;
    private final UserManagement _userManagement;
    private final InvitationEmailer.Factory _emailerFactory;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;

    public SharedFolderManagement(ISharedFolderDatabase db, UserManagement userManagement,
            InvitationEmailer.Factory factEmailer, User.Factory factUser,
            Organization.Factory factOrg)
    {
        _db = db;
        _userManagement = userManagement;
        _emailerFactory = factEmailer;
        _factUser = factUser;
        _factOrg = factOrg;
    }

    private void updateSharedFolderName(SID sid, String folderName, UserID userId)
            throws SQLException
    {
        // Update the folder name only when an owner changes it
        if (_db.getUserPermissionForStore(sid, userId) == Role.OWNER) {
            _db.setFolderName(sid, folderName);
        }
    }

    /**
     * Shares the given folder with the given set of subject-role pairs.
     * @param emails invitation emails to be sent once the transaction is committed (out arg)
     * @return a map of user IDs and epochs to be returned via verkehr.
     */
    public Map<UserID, Long> shareFolder(String folderName, SID sid, UserID userId,
            List<SubjectRolePair> rolePairs, @Nullable String note, List<InvitationEmailer> emails)
            throws Exception
    {
        User sharer = _factUser.create(userId);
        sharer.throwIfNotFound();

        // Check that the user is verified - only verified users can share
        if (!sharer.isVerified()) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm("user " + userId + " is not yet verified");
        }

        // Move forward with setting ACLs and sharing the folder
        // NB: if any user fails business rules check, the whole transaction is rolled back
        Map<UserID, Long> epochs = createACL(userId, sid, rolePairs);
        updateSharedFolderName(sid, folderName, userId);

        for (SubjectRolePair rolePair : rolePairs) {
            emails.add(shareFolderWithOne(sharer, rolePair._subject, folderName, sid, note));
        }

        return epochs;
    }

    private InvitationEmailer shareFolderWithOne(final User sharer, UserID shareeId,
            final String folderName, SID sid, @Nullable final String note)
            throws Exception
    {
        InvitationEmailer emailer;
        Organization shareeOrg;
        final User sharee = _factUser.create(shareeId);
        if (!sharee.exists()) {  // Sharing with a non-AeroFS user.
            shareeOrg = _factOrg.create(OrgID.DEFAULT);
            createFolderInvitation(sharer.id(), shareeId, sid, folderName);
            emailer = _userManagement.inviteOneUser(sharer, shareeId, shareeOrg, folderName,
                    note);
        } else if (!sharee.id().equals(sharer.id())) {
            final String code = createFolderInvitation(sharer.id(), sharee.id(), sid, folderName);
            emailer = _emailerFactory.createFolderInvitation(sharer.id().toString(),
                    sharee.id().toString(), sharer.getFullName()._first, folderName, note, code);
        } else {
            // do not create invite code for/send email to the requester
            l.warn(sharer + " tried to invite himself");
            emailer = new InvitationEmailer();
        }

        l.info("folder sharer " + sharer + " sharee " + shareeId);
        return emailer;
    }

    /**
     * Creates a folder invitation in the database
     * @return the share folder code
     */
    private String createFolderInvitation(UserID sharerId, UserID shareeId, SID sid,
            String folderName)
            throws SQLException
    {
        // TODO check BOTH sharer and sharee's organization allow this cross-organization sharing.

        String code = InvitationCode.generate(CodeType.SHARE_FOLDER);

        _db.addShareFolderCode(code, sharerId, shareeId, sid, folderName);

        return code;
    }

    /**
     * Create ACLs for a store.
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    private Map<UserID, Long> createACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm
    {
        l.info(requester + " create roles for s:" + sid);

        checkUserPermissionsAndClearACLForHijackedRootStore(requester, sid, pairs);

        // to satisfy foreign key constraints add the sid before creating ACLs
        // TODO (WW) this smells. Remove it.
        _db.addSharedFolder(sid);

        l.info(requester + " creating " + pairs.size() + " roles for s:" + sid);

        _db.createACL(requester, sid, pairs);

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _db.incrementACLEpoch(_db.getACLUsers(sid));
    }

    /**
     * This method checks whether the user has the right permissions needed to modify the
     * given store, and if not performs checks to detect malicious changes to permissions and
     * attempts to repair the store's permissions if needed. Updates pairs in place during the
     * repair process.
     */
    private void checkUserPermissionsAndClearACLForHijackedRootStore(UserID userId, SID sid,
            /* outarg */ List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm
    {
        if (canUserModifyACL(userId, sid)) return;

        // apparently the user cannot modify the ACL - check if an attacker maliciously
        // overwrote their permissions and repair the store if necessary

        l.info(userId + " cannot modify acl for s:" + sid);

        if (!SID.rootSID(userId).equals(sid)) {
            throw new ExNoPerm(userId + " not owner"); // nope - just a regular store
        }

        l.info(sid + " matches " + userId + " root store - delete existing acl");

        _db.deleteACL(sid);

        // add the userId as owner of the store
        boolean foundOwner = false;
        for (SubjectRolePair pair : pairs) {
            if (pair._subject.equals(userId) && pair._role.equals(Role.OWNER)) {
                foundOwner = true;
            }
        }

        if (!foundOwner) pairs.add(new SubjectRolePair(userId, Role.OWNER));
    }

    /**
     * <strong>Call in the context of an overall transaction only!</strong>
     *
     * @param userId person requesting the ACL changes
     * @param sid store to which the acl changes will be made
     * @return true if the ACL changes should be allowed (i.e. the user has permissions)
     */
    private boolean canUserModifyACL(UserID userId, SID sid)
            throws SQLException
    {
        if (!_db.hasACL(sid)) {
            l.info("allow acl modification - no roles exist for s:" + sid);
            return true;
        } else if (_db.isOwner(sid, userId)) {
            l.info(userId + " is an owner for s:" + sid);
            return true;
        } else {
            l.info(userId + " cannot modify acl for " + sid);
            return false;
        }

        // The following check is not necessary for the current requirement.
//        // see if user is an admin and one of their organization's members is an owner
//        User currentUser = getUserNullable(userId);
//        assert currentUser != null;
//        if (currentUser.getLevel() == AuthorizationLevel.ADMIN) {
//            l.info("user is an admin, checking if folder owner(s) are part of organization");
//
//            PreparedStatement ps = getConnection().prepareStatement(
//                    "select count(*) from " + T_AC + " join " + T_USER + " on " + C_AC_USER_ID +
//                            "=" + C_USER_ID + " where " + C_AC_STORE_ID + "=? and " + C_USER_ORG_ID +
//                            "=? and " + C_AC_ROLE + "=?");
//
//            ps.setBytes(1, sid.getBytes());
//            ps.setInt(2, currentUser.getOrgID().getInt());
//            ps.setInt(3, Role.OWNER.ordinal());
//
//            ResultSet rs = ps.executeQuery();
//            try {
//                Util.verify(rs.next());
//                int ownersInUserOrgCount = rs.getInt(1);
//                l.info("there is/are " + ownersInUserOrgCount + " folder owner(s) in " + userId +
//                        "'s organization");
//                assert !rs.next();
//                if (ownersInUserOrgCount > 0) {
//                    return true;
//                }
//            } finally {
//                rs.close();
//            }
//        }
    }

    /**
     * Update ACLs for a store
     * @throws ExNoPerm if trying to add new users to the store
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    public Map<UserID, Long> updateACL(UserID requester, SID sid, List<SubjectRolePair> srps)
            throws ExNoPerm, SQLException, ExBadArgs
    {
        l.info(requester + " updating " + srps.size() + " roles for " + sid);

        if (srps.isEmpty()) throw new ExBadArgs("Must specify one or more subjects");

        checkUserPermissionsAndClearACLForHijackedRootStore(requester, sid, srps);

        _db.updateACL(requester, sid, srps);

        if (!_db.hasOwner(sid)) throw new ExNoPerm("Cannot demote all admins");

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _db.incrementACLEpoch(_db.getACLUsers(sid));
    }

    public Map<UserID, Long> deleteACL(UserID requester, SID sid, Collection<UserID> subjects)
            throws SQLException, ExNoPerm, ExBadArgs
    {
        l.info(requester + " delete roles for " + sid + ": " + subjects);

        if (subjects.isEmpty()) throw new ExBadArgs("must specify one or more subjects");

        if (!canUserModifyACL(requester, sid)) {
            l.info(requester + " cannot modify acl for s:" + sid);
            throw new ExNoPerm();
        }

        // retrieve the list of affected users _before_ performing the deletion, so that all the
        // users including the deleted ones will get notifications.
        Set<UserID> affectedUsers = _db.getACLUsers(sid);

        _db.deleteACL(requester, sid, subjects);

        if (!_db.hasOwner(sid)) throw new ExNoPerm("cannot demote all admins");

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _db.incrementACLEpoch(affectedUsers);
    }
}
