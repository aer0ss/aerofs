package com.aerofs.auth;

import com.aerofs.auth.cert.AeroDevicePrincipal;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Implementation of {@link SecurityContext} that
 * represents an authenticated AeroFS entity.
 */
public final class AeroSecurityContext implements SecurityContext {

    private final AeroDevicePrincipal principal;
    private final String role;
    private final String authenticationScheme;

    public AeroSecurityContext(String user, String device, String role, String authenticationScheme) {
        this.principal = new AeroDevicePrincipal(user, device);
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
        return true; // FIXME (AG): unsure what this should be and how it should be set
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }
}
