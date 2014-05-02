package com.aerofs.daemon.rest.util;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.rest.util.AuthToken;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

import static com.aerofs.base.acl.Permissions.VIEWER;
import static com.google.common.base.Preconditions.checkState;

/**
 * Turn RestObject into OA, with ACL checking
 */
public class RestObjectResolver
{
    private final static Logger l = Loggers.getLogger(RestObjectResolver.class);

    @Inject private LocalACL _acl;
    @Inject private DirectoryService _ds;
    @Inject private IMapSID2SIndex _sid2sidx;
    @Inject private IMapSIndex2SID _sidx2sid;
    @Inject private IStores _stores;
    @Inject private IOSUtil _os;
    @Inject private CfgStorageType _storageType;
    @Inject private CfgAbsRoots _absRoots;

    // sigh, that's not great but we need these to auto-provision appdata folder
    @Inject private ObjectCreator _oc;
    @Inject private TransManager _tm;

    public OA resolveFollowsAnchor_(RestObject object, AuthToken token)
            throws ExNotFound, SQLException
    {
        try {
            return resolveFollowsAnchorWithPermissions_(object, token, VIEWER);
        } catch (ExNoPerm e) {
            // avoid leaking info about existence of resource
            throw new ExNotFound();
        }
    }

    public OA resolve_(RestObject object, AuthToken token) throws ExNotFound, SQLException
    {
        try {
            return resolveWithPermissions_(object, token, VIEWER);
        } catch (ExNoPerm e) {
            // avoid leaking info about existence of resource
            throw new ExNotFound();
        }
    }

    public OA resolveFollowsAnchorWithPermissions_(RestObject object, AuthToken token,
            Permissions permissions)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = resolveImpl_(object, token);
        if (oa.isAnchor()) {
            try {
                oa = _ds.getOAThrows_(_ds.followAnchorThrows_(oa));
            } catch (ExExpelled e) {
                throw new ExNotFound();
            }
        }
        _acl.checkThrows_(token.user, oa.soid().sidx(), permissions);
        return oa;
    }

    public OA resolveWithPermissions_(RestObject object, AuthToken token, Permissions permissions)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = resolveImpl_(object, token);
        _acl.checkThrows_(token.user, oa.soid().sidx(), permissions);
        return oa;
    }

    private static final String APPDATA = ".appdata";

    public static Path appDataPath(AuthToken token)
    {
        return Path.fromString(SID.rootSID(token.user), APPDATA);
    }

    private void markHidden(Path p)
    {
        if (_storageType.get() != StorageType.LINKED) return;
        try {
            _os.markHiddenSystemFile(p.toAbsoluteString(_absRoots.getNullable(p.sid())));
        } catch (Exception e) {}
    }

    private SOID createAppDataIfMissing(AuthToken token) throws SQLException, ExNotFound
    {
        Path p = appDataPath(token);
        SOID soid = _ds.resolveNullable_(p);
        Trans t = _tm.begin_();
        try {
            if (soid == null) {
                soid = _oc.create_(Type.DIR, _ds.resolveNullable_(p.removeLast()), APPDATA,
                        PhysicalOp.APPLY, t);
                markHidden(p);
            }
            OID oid = _ds.getChild_(soid.sidx(), soid.oid(), token.app);
            if (oid == null) {
                soid = _oc.create_(Type.DIR, soid, token.app, PhysicalOp.APPLY, t);
            } else {
                soid = new SOID(soid.sidx(), oid);
            }
            t.commit_();
            return soid;
        } catch (Exception e) {
            throw new ExNotFound("Failed to create appdata");
        } finally {
            t.end_();
        }
    }

    private OA resolveImpl_(RestObject object, AuthToken token) throws ExNotFound, SQLException
    {
        OA oa;
        if (object.isAppData()) {
            oa = _ds.getOAThrows_(createAppDataIfMissing(token));
        } else {
            SID sid = object.sid;
            OID oid = object.oid;
            if (object.isRoot()) {
                sid = SID.rootSID(token.user);
                oid = OID.ROOT;
            }
            SIndex sidx = _sid2sidx.getNullable_(sid);
            if (sidx == null) throw new ExNotFound();

            SOID soid = new SOID(sidx, oid);
            oa = _ds.getOAThrows_(soid);
        }

        if (oa.soid().oid().isTrash() || _ds.isDeleted_(oa)) throw new ExNotFound();

        return oa;
    }

    /**
     * At any given time, a user should have at most one admitted anchor for each
     * shared folder it is a member of.
     *
     * If no anchor is present then the folder is an "external" share for that user,
     * aka an alternate root.
     */
    OA parentForUser(SIndex sidx, UserID user) throws SQLException
    {
        SID sid = _sidx2sid.get_(sidx);
        if (sid.isUserRoot()) return null;
        OID anchor = SID.storeSID2anchorOID(sid);
        for (SIndex sidxParent : _stores.getParents_(sidx)) {
            OA oa = _ds.getOANullable_(new SOID(sidxParent, anchor));
            if (oa != null && !oa.isExpelled() && _acl.check_(user, sidx, VIEWER)) {
                return oa;
            }
        }
        return null;
    }

    /**
     * On TeamServer DirectoryService.resolve stops at shared folder boundary
     *
     * For the API we want to provide a consistent view for every user, regardless
     * of whether they contact their own devices or Team Server. For this to work
     * we need to walk up, past the shared folder boundary, and pick the appropriate
     * parent store for the user making the request.
     */
    public ResolvedPath resolve(OA oa, UserID user)
            throws SQLException
    {
        ResolvedPath p = _ds.resolve_(oa);
        SIndex sidx = _sid2sidx.get_(p.sid());
        OA anchor;
        while ((anchor = parentForUser(sidx, user)) != null) {
            checkState(!anchor.soid().sidx().equals(sidx));
            p = _ds.resolve_(anchor).join(p);
            sidx = anchor.soid().sidx();
        }
        l.debug("resolve {} for {} -> {}", oa.soid(), user, p);
        return p;
    }
}
