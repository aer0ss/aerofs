/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.util;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.MDID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.oauth.VerifyTokenResponse;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class AuthToken
{
    /**
     * OAuth scopes supported by the API
     *
     * some scopes are "qualifiable", i.e. they can be restricted to a specific
     * subset of the data controlled by the scope. For now the only qualifiers
     * supported are shared folders but in the future it is conceivable that
     * SIDOID or path could be supported as well.
     */
    public static enum Scope
    {
        READ_FILES("files.read", true),
        WRITE_FILES("files.write", true),
        READ_ACL("acl.read", true),
        WRITE_ACL("acl.write", true),
        MANAGE_INVITATIONS("acl.invitations", false),
        READ_USER("user.read", false),
        WRITE_USER("user.write", false),
        MANAGE_PASSWORD("user.password", false),
        ;

        private final String name;
        private final boolean qualifiable;

        Scope(String name, boolean qualifiable)
        {
            this.name = name;
            this.qualifiable = qualifiable;
        }

        private final static Map<String, Scope> NAMES;
        static {
            NAMES = Maps.newHashMap();
            for (Scope scope : values()) checkState(NAMES.put(scope.name, scope) == null);
        }

        static @Nullable Scope fromName(String name)
        {
            return NAMES.get(name);
        }
    }

    private final static Logger l = LoggerFactory.getLogger(AuthToken.class);

    public final UserID user;
    public final DID did;
    public final OrganizationID org;

    //empty set means unrestricted scope
    public final Map<Scope, Set<SID>> scopes;

    public AuthToken(VerifyTokenResponse response) throws ExFormatError
    {
        user = response.principal.getUserID();
        did = new MDID(UniqueID.fromStringFormal(response.mdid));
        org = response.principal.getOrganizationID();
        scopes = parseScopes(response.scopes);
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
            } else if (s.length == 2 && scope.qualifiable) {
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
        checkArgument(!scope.qualifiable);
        Set<SID> sids = scopes.get(scope);
        return sids != null && sids.isEmpty();
    }

    public boolean hasFolderPermission(Scope scope, SID sid)
    {
        checkArgument(scope.qualifiable);
        Set<SID> sids = scopes.get(scope);
        return sids != null && (sids.isEmpty() || sids.contains(sid));
    }
}
