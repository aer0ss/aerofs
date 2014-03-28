/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.daemon.core.activity.ActivityLog;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.AbstractRestEBIMC;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.lib.event.Prio;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.util.AuthToken;
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


    // TODO: handle fine-grained token scoping
    protected static boolean hasAccessToFile(AuthToken token, Scope scope, ResolvedPath path)
    {
        checkArgument(scope == Scope.READ_FILES || scope == Scope.WRITE_FILES);
        return (token.hasPermission(Scope.APPDATA) && path.isUnderOrEqual(appDataPath(token)))
                || token.hasPermission(scope);
    }

    protected static void requireAccessToFile(AuthToken token, Scope scope, ResolvedPath path)
    {
        if (hasAccessToFile(token, scope, path)) return;

        throw new WebApplicationException(Response
                .status(Status.FORBIDDEN)
                .entity(new Error(Type.FORBIDDEN, "Token lacks required scope"))
                .build());
    }

    protected ResolvedPath requireAccessToFile(AuthToken token, Scope scope, OA oa)
            throws SQLException
    {
        ResolvedPath path = _access.resolve(oa, token.user);
        requireAccessToFile(token, scope, path);
        return path;
    }
}
