/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;

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
    /**
     * This method returns the permission a specific user has for a specific store, or null if
     * no permission for the given combination of user/store exists.
     */
    Role getUserPermissionForStore(SID sid, UserID userId)
            throws SQLException;

    /**
     * Create ACLs for a store. Replace existing entries if any.
     */
    void createACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm;

    void updateACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm;

    /**
     * This method performs an upsert on the shared folder name table, inserting a new row if there
     * is no name saved for the given store id or updating the row there with a new name
     * @param sid store id for the shared folder whose name is being updated
     * @param folderName new folder name
     */
    void setFolderName(SID sid, String folderName)
            throws SQLException;

    /**
     * Adds a new invite code to the database
     * @param code invite code generated for this shared folder
     * @param from the user inviting to the share
     * @param to the user invited to the share
     * @param sid the store id of the share for this invite code
     * @param folderName the user-facing name of the folder
     */
    void addShareFolderCode(String code, UserID from, UserID to, SID sid, String folderName)
            throws SQLException;

    void deleteACL(UserID userId, SID sid, Collection<UserID> subjects)
            throws SQLException;

    void deleteACL(SID sid)
            throws SQLException;

    boolean hasACL(SID sid)
        throws SQLException;

    boolean isOwner(SID sid, UserID userId)
        throws SQLException;

    Set<UserID> getACLUsers(SID sid)
        throws SQLException;

    void addSharedFolder(SID sid)
        throws SQLException;

    /**
     * @param users set of user_ids for all users for which we will update the epoch
     * @return a map of user -> updated epoch number
     */
    Map<UserID, Long> incrementACLEpoch(Set<UserID> users)
            throws SQLException;

    boolean hasOwner(SID sid) throws SQLException;
}
