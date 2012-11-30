/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.ISharedFolderDatabase;
import com.aerofs.sp.server.lib.ISharedFolderDatabase.FolderInvitation;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.user.UserManagement;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
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

    /**
     * Shares the given folder with the given set of subject-role pairs.
     * @param emails invitation emails to be sent once the transaction is committed (out arg)
     * @return a map of user IDs and epochs to be returned via verkehr.
     */
    public Map<UserID, Long> shareFolder(String folderName, SID sid, UserID userId,
            List<SubjectRolePair> rolePairs, @Nullable String note, List<InvitationEmailer> emails)
            throws ExNoPerm, ExNotFound, ExAlreadyExist, SQLException, IOException
    {
        User sharer = _factUser.create(userId);
        sharer.throwIfNotFound();

        // Check that the user is verified - only verified users can share
        if (!sharer.isVerified()) {
            // TODO (GS): We want to throw a specific exception if the inviter isn't verified
            // to allow easier error handling on the client-side
            throw new ExNoPerm("user " + userId + " is not yet verified");
        }

        Map<UserID, Long> epochs;

        if (_db.exists(sid)) {
            // folder already exists, check that the sender is an owner
            if (!_db.isOwner(sid, userId)) throw new ExNoPerm("Only owner can add new users");

            // no ACL change until sharees accept invite...
            epochs = Maps.newHashMap();
        } else {
            // create shared folder
            _db.addSharedFolder(sid, folderName);

            // add sharer as OWNER
            _db.createACL(sid, Lists.newArrayList(new SubjectRolePair(userId, Role.OWNER)));

            // increment ACL epoch for sharer
            epochs = _db.incrementACLEpoch(Collections.singleton(userId));
        }

        // build email notifications, enforcing business checks as we go
        // it's safe to do that step last as the whole operation is done transactionally and all
        // DB changes will be rolled back if any business checks fails
        for (SubjectRolePair rolePair : rolePairs) {
            emails.add(shareFolderWithOne(sharer, rolePair._subject, rolePair._role, folderName,
                    sid, note));
        }

        return epochs;
    }

    public Map<UserID, Long> joinSharedFolder(SID sid, UserID userId, Role role)
            throws ExAlreadyExist, ExNoPerm, SQLException
    {
        if (_db.getUserRoleForStore(sid, userId) != null) {
            // old invite/join workflow: ACL added on invite
            // TODO: remove this codepath after transition period...
            return Collections.emptyMap();
        } else {
            _db.createACL(sid, Lists.newArrayList(new SubjectRolePair(userId, role)));

            // increment ACL epoch for all users currently sharing the folder
            // making the modification to the database, and then getting the current acl list should
            // be done in a single atomic operation. Otherwise, it is possible for us to send out a
            // notification that is newer than what it should be (i.e. we skip an update
            return _db.incrementACLEpoch(_db.getACLUsers(sid));
        }
    }

    private InvitationEmailer shareFolderWithOne(final User sharer, UserID shareeId, Role role,
            final String folderName, SID sid, @Nullable final String note)
            throws ExNotFound, ExAlreadyExist, SQLException, IOException
    {
        InvitationEmailer emailer;
        final User sharee = _factUser.create(shareeId);
        if (!sharee.exists()) {  // Sharing with a non-AeroFS user.
            createFolderInvitation(sharer.id(), shareeId, role, sid, folderName);
            emailer = _userManagement.inviteOneUser(sharer, shareeId, _factOrg.getDefault(),
                    folderName, note);
        } else if (!sharee.id().equals(sharer.id())) {
            String code = createFolderInvitation(sharer.id(), sharee.id(), role, sid, folderName);
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
    private String createFolderInvitation(UserID sharerId, UserID shareeId, Role role, SID sid,
            String folderName)
            throws SQLException
    {
        // TODO check BOTH sharer and sharee's organization allow this cross-organization sharing.

        String code = InvitationCode.generate(CodeType.SHARE_FOLDER);

        // TODO: prevent multiple invitation?

        _db.addFolderInvitation(sharerId,
                new FolderInvitation(sid, folderName, shareeId, role, code));

        return code;
    }


    public List<String> getSharedFolderNames(UserID userId, List<ByteString> sids)
            throws ExNoPerm, ExNotFound, SQLException
    {
        List<String> names = Lists.newArrayListWithCapacity(sids.size());

        for (ByteString b : sids) {
            SID sid = new SID(b);
            if (_db.getUserRoleForStore(sid, userId) == null) {
                throw new ExNoPerm(sid.toStringFormal());
            }

            String name = _db.getSharedFolderName(sid);

            if (name == null) {
                throw new ExNotFound(sid.toStringFormal());
            }

            names.add(name);
        }

        return names;
    }

    /**
     * It is possible for a malicious user to create a shared folder whose SID collide with the
     * SID of somebody's root store. When a new user signs up we can check if such a colliding
     * folder exists and delete it to prevent malicious users from gaining access to other users'
     * root stores.
     *
     * TODO:
     * Ideally we should  also register root stores in SP to make sure creation of colliding shared
     * folder is impossible *after* the targeted user signs up. This is not currently done but will
     * have to be done regardless of security considerations to support the team server (the only
     * legitimate case where a root store need to be accessed by another user)
     *
     * NB: this check used to be done when creating/updating ACLs but that did not make sense as ACL
     * are never created/updated for root stores
     */
    public void checkForRootStoreCollision(UserID userId) throws SQLException
    {
        SID sid = SID.rootSID(userId);
        if (!_db.exists(sid)) return;

        // Looks like somebody created a shared folder that collides with somebody else's root store
        // NB: here we assume a malicious collision for eavesdropping intent however there is a
        // small but not necessarily negligible probability that real collision will occur. What we
        // should do in this case is left as an exercise to the reader.
        l.warn("Existing shared folder collides with root store id for user: " + userId.toString());

        _db.deleteSharedFolder(sid);
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
     * @throws ExNotFound if trying to add new users to the store
     * @return new ACL epochs for each affected user id, to be published via verkehr
     */
    public Map<UserID, Long> updateACL(UserID requester, SID sid, List<SubjectRolePair> srps)
            throws ExBadArgs, ExNoPerm, ExNotFound, SQLException
    {
        l.info(requester + " updating " + srps.size() + " roles for " + sid);

        if (srps.isEmpty()) throw new ExBadArgs("Must specify one or more subjects");

        _db.updateACL(sid, srps);

        if (!_db.hasOwner(sid)) throw new ExNoPerm("Cannot demote all admins");

        // making the modification to the database, and then getting the current acl list should
        // be done in a single atomic operation. Otherwise, it is possible for us to send out a
        // notification that is newer than what it should be (i.e. we skip an update

        return _db.incrementACLEpoch(_db.getACLUsers(sid));
    }

    public Map<UserID, Long> deleteACL(UserID requester, SID sid, Collection<UserID> subjects)
            throws SQLException, ExNotFound, ExNoPerm, ExBadArgs
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
