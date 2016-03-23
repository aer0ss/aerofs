package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.OAuthToken;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;

public class DaemonRestContentHelper extends RestContentHelper
{
    @Inject private DirectoryService _ds;
    @Inject private RestObjectResolver _access;

    private SIndex getSIndex(OA oa)
    {
        return oa.isAnchor() ?
                _sid2sidx.getNullable_(SID.anchorOID2storeSID(oa.soid().oid())) : oa.soid().sidx();
    }

    @Override
    void checkDeviceHasFileContent(SOID soid) throws ExNotFound, SQLException
    {
        OA oa = _ds.getOA_(soid);
        final CA ca = oa.caMasterNullable();
        if (ca == null) {
            String message;
            if (oa.isExpelled()) {
                message = _ds.isDeleted_(oa) ? "No such file" :
                        "Content not synced on this device";
            } else if (!_csdb.isCollectingContent_(oa.soid().sidx())) {
                message = "Quota exceeded";
            } else {
                message = "Content not yet available on this device";
            }
            throw new ExNotFound(message);
        }
    }

    @Override
    public ContentHash content(SOKID sokid) throws SQLException {
        return _ds.getCAHash_(sokid);
    }

    @Override
    SOID resolveObjectWithPerm(RestObject object, OAuthToken token,
            Scope scope, Permissions perms) throws Exception
    {
        SOID soid = resolveObjectWithPerm(object, token, perms);
        waitForFile(soid);
        OA oa = _ds.getOAThrows_(soid);
        if (!oa.isFile()) throw new ExNotFound("No such file");
        if (oa.isExpelled()) {
            throw new ExNotFound(_ds.isDeleted_(oa)
                    ? "No such file" : "Content not synced on this device");
        }
        checkState(oa != null, "Rest object was never resolved.");
        verifyTokenScopeAccessToFile(token, scope, getSIndex(oa),
                _access.resolve(oa, token.user()));

        return oa.soid();
    }

    @Override
    public KIndex selectBranch(SOID soid) throws SQLException
    {
        OA oa = _ds.getOA_(soid);
        // check if we have conflict and return conflict branch
        if (oa.cas().size() <= 1) {
            return KIndex.MASTER;
        }
        checkState(oa.cas().size() <= 2, "Found %d CAs for object %s", oa.cas().size(), oa.soid());
        return oa.cas().keySet().stream().
                findFirst().filter(kidx -> !kidx.equals(KIndex.MASTER)).get();
    }

    @Override
    boolean wasPresent(SOID soid) throws SQLException
    {
        return _ds.getOA_(soid).caMasterNullable() != null;
    }

    @Override
    void updateContent(SOID soid, ContentHash h, Trans t, long length, long mtime,
            boolean wasPresent) throws SQLException
    {
        // update CA
        if (!wasPresent) _ds.createCA_(soid, KIndex.MASTER, t);
        _ds.setCA_(new SOKID(soid, KIndex.MASTER), length, mtime, h, t);
    }
}
