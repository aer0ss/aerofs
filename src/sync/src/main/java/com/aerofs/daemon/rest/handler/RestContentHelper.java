package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.OAuthToken;
import com.google.inject.Inject;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class RestContentHelper
{
    @Inject protected LocalACL _acl;
    @Inject protected IMapSID2SIndex _sid2sidx;
    @Inject protected ICollectorStateDatabase _csdb;

    private SOID resolveObject(RestObject object, OAuthToken token)
    {
        SID sid = object.getSID();
        OID oid = object.getOID();
        if (oid.equals(OID.ROOT)) {
            if (sid.equals(SID.rootSID(token.user()))) {
                return new SOID(_sid2sidx.get_(sid), oid);
            } else {
                return new SOID(_sid2sidx.get_(SID.rootSID(token.user())), SID.storeSID2anchorOID(sid));
            }
        }
        return new SOID(_sid2sidx.get_(sid), oid);
    }

    private boolean hasAccessToFile(OAuthToken token, Scope scope, SIndex sidx, ResolvedPath path)
            throws SQLException
    {
        checkArgument(scope == Scope.READ_FILES || scope == Scope.WRITE_FILES);

        if (token.hasPermission(Scope.LINKSHARE)) {
            if (sidx == null) return false;
            if (!_acl.check_(token.user(), sidx, Permissions.allOf(Permissions.Permission.MANAGE))) return false;
        }
        if (token.hasPermission(Scope.APPDATA) &&
                path.isUnderOrEqual(new Path(SID.rootSID(token.user()), ".appdata"))) {
            return true;
        }
        if (!token.scopes.containsKey(scope)) return false;
        if (token.hasUnrestrictedPermission(scope)) return true;

        for (RestObject object : token.scopes.get(scope)) {
            if (path.soids.contains(resolveObject(object, token))) {
                return true;
            }
        }
        return false;
    }

    ResolvedPath requireAccessToFile(OAuthToken token, Scope scope, SIndex sidx,
            ResolvedPath path) throws SQLException
    {
        if (hasAccessToFile(token, scope, sidx, path)) return path;
        throw new WebApplicationException(Response
                .status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new com.aerofs.rest.api.Error(com.aerofs.rest.api.Error.Type.FORBIDDEN,
                        "Token lacks required scope"))
                .build());
    }

    abstract SOID resolveObjectWithPerm(RestObject object, OAuthToken token, Scope scope,
            Permissions perms) throws SQLException, ExNotFound, ExNoPerm;
    public abstract KIndex selectBranch(SOID soid) throws SQLException;
    abstract boolean wasPresent(SOID soid) throws SQLException;
    abstract void updateContent(SOID soid, ContentHash h, Trans t, long length, long mtime,
        boolean wasPresent) throws SQLException;
    abstract void checkDeviceHasFile(SOID soid) throws ExNotFound, SQLException;

    public abstract ContentHash content(SOKID sokid) throws SQLException;
}
