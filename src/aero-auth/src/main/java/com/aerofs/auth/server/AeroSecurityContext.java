package com.aerofs.auth.server;

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

    /**
     * Constructor.
     *
     * @param principal entity identified using an AeroFS-defined authentication scheme
     * @param role type of role (defined in {@link Roles}) associated with this entity
     * @param authenticationScheme method by which the entity was authenticated
     */
    public AeroSecurityContext(AeroPrincipal principal, String role, String authenticationScheme) { // FIXME (AG): may have to support multiple roles per principal
        this.principal = principal;
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
        return false; // FIXME (AG): unsure how to determine and set this
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }
}
