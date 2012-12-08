/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UserID;

import java.util.Map;

/**
 * Join/leave a store upon ACL changes, with multiplicity-specific behavior
 *
 * TODO: find a better name...
 */
public interface IStoreJoiner
{
    /**
     * Create logical/physical objects necessary to complete joining a store to which
     * we recently got access.
     *
     * @param newRoles the new ACL of the store
     */
    void joinStore_(SIndex sidx, SID sid, String folderName, Map<UserID, Role> newRoles, Trans t)
            throws Exception;

    /**
     * Remove logical/physical objects to complete kickout of a store to which we recently lost
     * access.
     *
     * @param newRoles the new ACL of the store
     */
    void leaveStore_(SIndex sidx, SID sid, Map<UserID, Role> newRoles, Trans t) throws Exception;
}
