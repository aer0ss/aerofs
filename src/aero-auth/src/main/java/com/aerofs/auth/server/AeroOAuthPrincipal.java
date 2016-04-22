package com.aerofs.auth.server;

import com.aerofs.base.id.RestObject;
import com.aerofs.ids.*;
import com.aerofs.oauth.OAuthScopeParsingUtil;
import com.aerofs.oauth.Scope;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public class AeroOAuthPrincipal implements AeroPrincipal
{
    private final UserID user;
    private final String name;
    private final DID did;
    private final String audience;
    private final Map<Scope, Set<RestObject>> tokenScope;

    public AeroOAuthPrincipal(UserID userID, String name, String mdid, String audience,
            Set<String> tokenScope) throws ExInvalidID
    {
        this.user = userID;
        this.name = name;
        this.did = new DID(UniqueID.fromStringFormal(mdid));
        this.audience = audience;
        this.tokenScope = OAuthScopeParsingUtil.parseScopes(tokenScope);
    }

    public UserID getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public DID getDID() {
        return did;
    }

    public String audience() {
        return audience;
    }

    public Map<Scope, Set<RestObject>> scope() {
        return tokenScope;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AeroOAuthPrincipal other = (AeroOAuthPrincipal) o;
        return Objects.equal(user, other.user) && Objects.equal(name, other.name)
                && Objects.equal(did, other.did) && Objects.equal(audience, audience)
                && Objects.equal(tokenScope, other.tokenScope);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(user, name, did, audience, tokenScope);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("user", user)
                .add("name", name)
                .add("device", did)
                .add("audience", audience)
                .add("scope", tokenScope)
                .toString();
    }
}
