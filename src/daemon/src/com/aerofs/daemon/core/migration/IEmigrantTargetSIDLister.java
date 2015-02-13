/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.ids.OID;
import com.aerofs.ids.SID;

import java.sql.SQLException;
import java.util.List;

public interface IEmigrantTargetSIDLister
{
    /**
     * @param oidParent the parent OID of the object
     * @param name the name of the object
     * @return a list of SIDs ready to be filled into the PBMeta.emigrant_target_ancestor_sid field
     *      for the given object. an empty list for non-emigrant objects or if the target store
     *      doesn't exist locally
     */
    List<SID> getEmigrantTargetAncestorSIDsForMeta_(OID oidParent, String name)
            throws SQLException;
}
