/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.activity.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.AbstractRestEBIMC;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.util.OAuthToken;
import com.google.inject.Inject;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.sql.SQLException;

import static com.aerofs.daemon.rest.util.RestObjectResolver.appDataPath;
import static com.google.common.base.Preconditions.checkArgument;

public abstract class AbstractRestHdIMC<T extends AbstractRestEBIMC> extends AbstractHdIMC<T>
{
    @Inject protected DirectoryService _ds;
    @Inject protected TransManager _tm;
    @Inject protected RestObjectResolver _access;
    @Inject protected MetadataBuilder _mb;
    @Inject protected EntityTagUtil _etags;
    @Inject protected LocalACL _acl;
    @Inject protected IMapSID2SIndex _sid2sidx;

    @Override
    protected final void handleThrows_(T ev, Prio prio) throws Exception
    {
        try {
            ActivityLog.onBehalfOf(ev.did());
            handleThrows_(ev);
        } finally {
            ActivityLog.onBehalfOf(null);
        }
    }

    protected abstract void handleThrows_(T ev) throws Exception;

    protected boolean hasAccessToFile(OAuthToken token, Scope scope, OA oa, ResolvedPath path)
            throws SQLException
    {
        checkArgument(scope == Scope.READ_FILES || scope == Scope.WRITE_FILES);

        if (token.hasPermission(Scope.LINKSHARE)) {
            SIndex sidx = oa.isAnchor() ?
                    _sid2sidx.getNullable_(SID.anchorOID2storeSID(oa.soid().oid())) : oa.soid().sidx();
            if (sidx == null) return false;
            if (!_acl.check_(token.user(), sidx, Permissions.allOf(Permission.MANAGE))) return false;
        }

        if (token.hasPermission(Scope.APPDATA) && path.isUnderOrEqual(appDataPath(token))) {
            return true;
        }
        if (!token.scopes.containsKey(scope)) return false;
        if (token.hasUnrestrictedPermission(scope)) return true;

        OA oas;
        for (RestObject object : token.scopes.get(scope)) {
            try {
                oas = _access.resolve_(object, token);
            } catch (ExNotFound e) {
                // treat ExNotFound like a lack of permission and try the next object
                l.debug("could not resolve RestObject {}", object.toStringFormal());
                continue;
            }
            if (path.soids.contains(oas.soid())) {
                return true;
            }
        }
        return false;
   }

    protected ResolvedPath requireAccessToFile(OAuthToken token, Scope scope, OA oa)
            throws SQLException
    {
        ResolvedPath path = _access.resolve(oa, token.user());

        if (hasAccessToFile(token, scope, oa, path)) return path;

        throw new WebApplicationException(Response
                .status(Status.FORBIDDEN)
                .entity(new Error(Type.FORBIDDEN, "Token lacks required scope"))
                .build());
    }
}
