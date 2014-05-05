/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.util;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.MDID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.oauth.Scope;
import com.aerofs.oauth.VerifyTokenResponse;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class OAuthToken
    implements IUserAuthToken
{
    private final static Logger l = LoggerFactory.getLogger(OAuthToken.class);

    private final UserID user;
    private final UserID issuer;
    private final DID did;
    private final String app;
    //empty set means unrestricted scope
    private final Map<Scope, Set<SID>> scopes;

    public UserID user() { return user; }
    public UserID issuer() { return issuer; }
    public UniqueID uniqueId() { return did(); }
    public DID did() { return did; }
    public String app() { return app; }

    public OAuthToken(VerifyTokenResponse response) throws ExFormatError
    {
        issuer = response.principal.getIssuingUserID();
        user = response.principal.getEffectiveUserID();
        did = new MDID(UniqueID.fromStringFormal(response.mdid));
        scopes = parseScopes(response.scopes);
        app = response.audience;
    }

    private static Map<Scope, Set<SID>> parseScopes(Set<String> scopes)
    {
        Map<Scope, Set<SID>> m = Maps.newHashMap();
        for (String name : scopes) {
            String[] s = name.split(":");
            Scope scope = Scope.fromName(s[0]);
            if (scope == null) {
                l.warn("invalid scope name {}", s[0]);
                continue;
            }
            if (s.length == 1) {
                m.put(scope, Collections.<SID>emptySet());
            } else if (s.length == 2 && Scope.isQualifiable(scope)) {
                Set<SID> sids = m.get(scope);
                SID sid;
                try {
                    sid = SID.fromStringFormal(s[1]);
                } catch (ExFormatError e) {
                    l.warn("invalid scope qualifier {}", s[1]);
                    continue;
                }
                if (sids == null) {
                    m.put(scope, Sets.<SID>newHashSet(sid));
                } else if (!sids.isEmpty()) {
                    sids.add(sid);
                }
            } else {
                l.warn("invalid scope name {}", scope);
            }
        }
        return m;
    }

    // TODO: cert-based auth

    public boolean hasPermission(Scope scope)
    {
        Set<SID> sids = scopes.get(scope);
        return sids != null && sids.isEmpty();
    }

    public boolean hasFolderPermission(Scope scope, SID sid)
    {
        checkArgument(Scope.isQualifiable(scope));
        Set<SID> sids = scopes.get(scope);
        return sids != null && (sids.isEmpty() || sids.contains(sid));
    }
}
