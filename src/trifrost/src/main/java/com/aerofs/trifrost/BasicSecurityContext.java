package com.aerofs.trifrost;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Set;

/**
 */
public class BasicSecurityContext implements SecurityContext {

    private final Principal principal;
    private final Set<String> allowedRoles;

    public BasicSecurityContext(String user, Set<String> allowedRoles) {
        this.principal = () -> user;
        this.allowedRoles = allowedRoles;
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        return allowedRoles.contains(role);
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public String getAuthenticationScheme() {
        return SecurityContext.BASIC_AUTH;
    }
}
