/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.auth;

import javax.annotation.concurrent.Immutable;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Specialization of {@link SecurityContext} that
 * represents an unauthenticated connection.
 */
@Immutable
public final class UnauthenticatedSecurityContext implements SecurityContext {

    /**
     * Convenience instance of an {@link com.aerofs.baseline.auth.UnauthenticatedSecurityContext}.
     */
    public static final UnauthenticatedSecurityContext UNAUTHENTICATED_SECURITY_CONTEXT = new UnauthenticatedSecurityContext();

    @Override
    public Principal getUserPrincipal() {
        return null; // no authenticated user
    }

    @Override
    public boolean isUserInRole(String role) {
        return false; // since there was no authentication, there's no role
    }

    @Override
    public boolean isSecure() {
        return false; // not a secure connection
    }

    @Override
    public String getAuthenticationScheme() {
        return null; // no authentication scheme used
    }
}
