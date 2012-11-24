/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePair;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Interface for shared folder related database methods
 */
public interface ISharedFolderDatabase
{
    /**
     * This method returns the permission a specific user has for a specific store, or null if
     * no permission for the given combination of user/store exists.
     */
    public Role getUserPermissionForStore(SID sid, UserID userId)
            throws SQLException;

    /**
     * This method creates ACLs as necessary when adding new users to a shared folder
     * @return Map(userId to acl epoch) for use in verkehr notifications
     */
    public Map<UserID, Long> createACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm, IOException;

    /**
     * This method updates ACLs when changing user roles in an existing shared folder
     * @return Map(userId to acl epoch) for use in verkehr notifications
     */
    public Map<UserID, Long> updateACL(UserID requester, SID sid, List<SubjectRolePair> pairs)
            throws SQLException, ExNoPerm, IOException;

    /**
     * This method performs an upsert on the shared folder name table, inserting a new row if there
     * is no name saved for the given store id or updating the row there with a new name
     * @param sid store id for the shared folder whose name is being updated
     * @param folderName new folder name
     */
    public void setFolderName(SID sid, String folderName)
            throws SQLException;

    /**
     * Adds a new invite code to the database
     * @param code invite code generated for this shared folder
     * @param from the user inviting to the share
     * @param to the user invited to the share
     * @param sid the store id of the share for this invite code
     * @param folderName the user-facing name of the folder
     */
    public void addShareFolderCode(String code, UserID from, UserID to, SID sid, String folderName)
            throws SQLException;
}
