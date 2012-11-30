/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for shared folder related database methods
 *
 * TODO (WW) reorganize methods in this class. have this interface make more sense and remove
 * duplicate / similar methods.
 */
public interface ISharedFolderDatabase
{
    public static class FolderInvitation
    {
        public final SID _sid;
        public final String _folderName;
        public final UserID _invitee;
        public final Role _role;
        public final String _code;

        public FolderInvitation(SID sid, String folderName, UserID invitee, Role role, String code)
        {
            _sid = sid;
            _folderName = folderName;
            _invitee = invitee;
            _role = role;
            _code = code;
        }
    }

    /**
     * Adds a new invite code to the database
     */
    void addFolderInvitation(UserID from, FolderInvitation invitation) throws SQLException;

    void removeFolderInvitation(String code) throws SQLException;

    FolderInvitation getFolderInvitation(String code) throws SQLException;

    List<FolderInvitation> listPendingFolderInvitations(UserID to) throws SQLException;

    /**
     * @return whether the given shared folder exists
     */
    boolean exists(SID sid) throws SQLException;

    boolean hasACL(SID sid) throws SQLException;

    boolean hasOwner(SID sid) throws SQLException;

    Set<UserID> getACLUsers(SID sid) throws SQLException;


    /**
     * Creates a new shared folder
     */
    void addSharedFolder(SID sid, String name) throws SQLException;

    /**
     * Delete a shared, all its ACL entries and all related invites
     */
    void deleteSharedFolder(SID sid) throws SQLException;

    /**
     * @return shared folder name, null if no such shared folder exists
     */
    @Nullable String getSharedFolderName(SID sid) throws SQLException;

    /**
     * This method returns the permission a specific user has for a specific store, or null if
     * no permission for the given combination of user/store exists.
     */
    Role getUserRoleForStore(SID sid, UserID userId) throws SQLException;

    /**
     * Create ACLs for a store
     * @throws ExAlreadyExist if any of the subjects to add is already a member of the store
     */
    void createACL(SID sid, Iterable<SubjectRolePair> pairs)
            throws ExAlreadyExist, SQLException;

    /**
     * Update ACLs for a store
     * @throws ExNotFound if any of the subjects to update is not a member of the store
     */
    void updateACL(SID sid, Iterable<SubjectRolePair> pairs)
            throws ExNotFound, SQLException;

    /**
     * Delete ACLs for a store
     * @throws ExNotFound if any of the users to remove is not a member of the store
     */
    void deleteACL(UserID userId, SID sid, Collection<UserID> subjects)
            throws ExNotFound, SQLException;

    boolean isOwner(SID sid, UserID userId) throws SQLException;

    /**
     * @param users set of user_ids for all users for which we will update the epoch
     * @return a map of user -> updated epoch number
     */
    Map<UserID, Long> incrementACLEpoch(Set<UserID> users) throws SQLException;
}
