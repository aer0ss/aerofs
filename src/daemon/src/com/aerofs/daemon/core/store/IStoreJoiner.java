/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;

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
     */
    void joinStore_(SIndex sidx, SID sid, String folderName, Trans t) throws Exception;

    /**
     * Remove logical/physical objects to complete kickout of a store to which we recently lost
     * access.
     */
    void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception;
}
