package com.aerofs.oauth;

import java.util.Set;

/**
 * Representation of the answer to a Resource Server when asked to verify a token.
 */
public class VerifyTokenResponse {
    public static final VerifyTokenResponse NOT_FOUND = new VerifyTokenResponse("not_found");
    public static final VerifyTokenResponse EXPIRED = new VerifyTokenResponse("token_expired");

    public final String audience;
    public final Set<String> scopes;
    public final AuthenticatedPrincipal principal;
    public final Long expiresIn;
    public final String error;
    public final String mdid;

    public VerifyTokenResponse(String error)
    {
        this.audience = null;
        this.scopes = null;
        this.principal = null;
        this.expiresIn = null;
        this.mdid = null;
        this.error = error;
    }

    public VerifyTokenResponse(String audience, Set<String> scopes, Long expiresIn,
            AuthenticatedPrincipal principal, String mdid)
    {
        this.audience = audience;
        this.scopes = scopes;
        this.principal = principal;
        this.expiresIn = expiresIn;
        this.mdid = mdid;
        this.error = null;
    }
}
