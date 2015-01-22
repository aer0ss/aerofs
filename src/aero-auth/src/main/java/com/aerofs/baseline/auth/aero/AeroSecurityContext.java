package com.aerofs.baseline.auth.aero;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Implementation of {@link SecurityContext} that
 * represents an authenticated AeroFS entity.
 */
public final class AeroSecurityContext implements SecurityContext {

    private final AeroPrincipal principal;
    private final String role;
    private final String authenticationScheme;

    AeroSecurityContext(String user, String did, String provenance, String role, String authenticationScheme) {
        this.principal = new AeroPrincipal(user, did, provenance);
        this.role = role;
        this.authenticationScheme = authenticationScheme;
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        return this.role.equals(role);
    }

    @Override
    public boolean isSecure() {
        return true; // SSL terminated at nginx
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }
}
