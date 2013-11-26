package com.aerofs.daemon.rest.util;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.base.acl.Permissions.VIEWER;

/**
 * Turn RestObject into OA, with ACL checking
 */
public class RestObjectResolver
{
    private final LocalACL _acl;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public RestObjectResolver(LocalACL acl, DirectoryService ds, IMapSID2SIndex sid2sidx)
    {
        _acl = acl;
        _ds = ds;
        _sid2sidx = sid2sidx;
    }

    public OA resolveFollowsAnchor_(RestObject object, UserID user)
            throws ExNotFound, SQLException
    {
        try {
            return resolveFollowsAnchorWithPermissions_(object, user, VIEWER);
        } catch (ExNoPerm e) {
            // avoid leaking info about existence of resource
            throw new ExNotFound();
        }
    }

    public OA resolve_(RestObject object, UserID user) throws ExNotFound, SQLException
    {
        try {
            return resolveWithPermissions_(object, user, VIEWER);
        } catch (ExNoPerm e) {
            // avoid leaking info about existence of resource
            throw new ExNotFound();
        }
    }

    public OA resolveFollowsAnchorWithPermissions_(RestObject object, UserID user,
            Permissions permissions)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = resolve_(object);
        if (oa.isAnchor()) {
            try {
                oa = _ds.getOAThrows_(_ds.followAnchorThrows_(oa));
            } catch (ExExpelled e) {
                throw new ExNotFound();
            }
        }
        _acl.checkThrows_(user, oa.soid().sidx(), permissions);
        return oa;
    }

    public OA resolveWithPermissions_(RestObject object, UserID user, Permissions permissions)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = resolve_(object);
        _acl.checkThrows_(user, oa.soid().sidx(), permissions);
        return oa;
    }

    private OA resolve_(RestObject object) throws ExNotFound, SQLException
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
}
