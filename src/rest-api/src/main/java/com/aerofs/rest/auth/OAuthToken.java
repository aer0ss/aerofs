/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.auth;

import com.aerofs.base.id.RestObject;
import com.aerofs.ids.*;
import com.aerofs.oauth.OAuthScopeParsingUtil;
import com.aerofs.oauth.Scope;
import com.aerofs.oauth.VerifyTokenResponse;
import com.google.common.base.Joiner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class OAuthToken implements IUserAuthToken
{
    private final UserID user;
    private final UserID issuer;
    private final DID did;
    private final String app;

    // empty set means unrestricted scope
    public final Map<Scope, Set<RestObject>> scopes;

    @Override public UserID user() { return user; }
    @Override public UserID issuer() { return issuer; }
    @Override public UniqueID uniqueId() { return did(); }
    public DID did() { return did; }
    public String app() { return app; }

    public OAuthToken(VerifyTokenResponse response) throws ExInvalidID
    {
        issuer = response.principal.getIssuingUserID();
        user = response.principal.getEffectiveUserID();
        did = new DID(UniqueID.fromStringFormal(response.mdid));
        scopes = OAuthScopeParsingUtil.parseScopes(response.scopes);
        app = response.audience;
    }

    // TODO: cert-based auth

    @Override
    public boolean hasPermission(Scope scope)
    {
        Set<RestObject> objects = scopes.get(scope);
        return objects != null && objects.isEmpty();
    }

    /**
     * N.B. this checks only if the token is scoped exactly to that SID.
     */
    @Override
    public boolean hasFolderPermission(Scope scope, SID sid)
    {
        checkArgument(Scope.isQualifiable(scope));
        Set<RestObject> objects = scopes.get(scope);
        return objects != null && (objects.isEmpty() || objects.contains(new RestObject(sid)));
    }

    public boolean hasUnrestrictedPermission(Scope scope)
    {
        return !Scope.isQualifiable(scope) || Collections.<RestObject>emptySet().equals(scopes.get(scope));
    }

    @Override
    public String toString()
    {
        return "{" + Joiner.on(',').join(issuer, user, did, scopes) + "}";
    }
}
