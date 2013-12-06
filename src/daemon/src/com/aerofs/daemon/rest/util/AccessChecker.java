package com.aerofs.daemon.rest.util;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import java.sql.SQLException;

public class AccessChecker
{
    private final LocalACL _acl;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public AccessChecker(LocalACL acl, DirectoryService ds, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx)
    {
        _acl = acl;
        _ds = ds;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    public OA checkObjectFollowsAnchor_(RestObject object, UserID user)
            throws ExNotFound, SQLException
    {
        try {
            return checkObjectFollowsAnchor_(object, user, Role.VIEWER);
        } catch (ExNoPerm e) {
            // avoid leaking info about existence of resource
            throw new ExNotFound();
        }
    }

    public OA checkObject_(RestObject object, UserID user) throws ExNotFound, SQLException
    {
        try {
            return checkObject_(object, user, Role.VIEWER);
        } catch (ExNoPerm e) {
            // avoid leaking info about existence of resource
            throw new ExNotFound();
        }
    }

    public OA checkObjectFollowsAnchor_(RestObject object, UserID user, Role role)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = resolveObject_(object);
        if (oa.isAnchor()) {
            try {
                oa = _ds.getOAThrows_(_ds.followAnchorThrows_(oa));
            } catch (ExExpelled e) {
                throw new ExNotFound();
            }
        }
        _acl.checkThrows_(user, oa.soid().sidx(), role);
        return oa;
    }

    public OA checkObject_(RestObject object, UserID user, Role role)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = resolveObject_(object);
        _acl.checkThrows_(user, oa.soid().sidx(), role);
        return oa;
    }

    private OA resolveObject_(RestObject object) throws ExNotFound, SQLException
    {
        SID sid = object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        SOID soid = new SOID(sidx, object.oid);
        OA oa = _ds.getOAThrows_(soid);

        // the REST API ignores expelled object for now
        if (oa.isExpelled()) throw new ExNotFound();

        return oa;
    }

    public RestObject parent(RestObject o, OA oa) throws SQLException
    {
        if (oa.parent().isRoot()) {
            ImmutableList<SOID> l = _ds.resolve_(oa).soids;
            if (l.size() > 1) {
                SOID soid = l.get(l.size() - 2);
                return new RestObject(_sidx2sid.get_(soid.sidx()), soid.oid());
            }
        }
        return new RestObject(o.sid, oa.parent());
    }
}
