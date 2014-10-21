/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStoreHierarchy;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
import com.google.inject.Inject;


public class EmigrantTargetSIDLister implements IEmigrantTargetSIDLister
{
    private final SingleuserStoreHierarchy _sss;
    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public EmigrantTargetSIDLister(SingleuserStoreHierarchy sss, IMapSID2SIndex sid2sidx,
            IMapSIndex2SID sidx2sid)
    {
        _sss = sss;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
    }

    /**
     * @param oidParent the parent OID of the object
     * @param name the name of the object
     * @return a list of SIDs ready to be filled into the PBMeta.emigrant_target_ancestor_sid field
     *      for the given object. an empty list for non-emigrant objects or if the target store
     *      doesn't exist locally
     */
    @Override
    public List<SID> getEmigrantTargetAncestorSIDsForMeta_(OID oidParent, String name)
            throws SQLException
    {
        // TODO: do we need to check isDeleted_ instead? (in which store?)
        if (!oidParent.isTrash()) return Collections.emptyList();
        SID sid = EmigrantUtil.getEmigrantTargetSID(name);
        if (sid == null) return Collections.emptyList();
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) return Collections.emptyList();

        // get the parent store for non-root stores
        if (!_sss.isRoot_(sidx)) sidx = _sss.getParent_(sidx);

        ArrayList<SID> ret = Lists.newArrayListWithCapacity(2);
        while (true) {
            ret.add(_sidx2sid.get_(sidx));
            if (_sss.isRoot_(sidx)) break;
            sidx = _sss.getParent_(sidx);
        }

        return ret;
    }
}
