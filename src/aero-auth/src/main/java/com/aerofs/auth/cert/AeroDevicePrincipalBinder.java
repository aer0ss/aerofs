package com.aerofs.auth.cert;

import com.google.common.base.Preconditions;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * HK2 {@link org.glassfish.hk2.utilities.Binder} that injects
 * instances of {@link AeroDevicePrincipal} derived from the incoming
 * {@link javax.ws.rs.core.Request} object's {@link SecurityContext}.
 */
public final class AeroDevicePrincipalBinder extends AbstractBinder {

    @Override
    protected void configure() {
        // note that this is how the Jersey docs recommend we do this
        // see: https://jersey.java.net/documentation/latest/ioc.html
        // create a new instance of the factory every time
        // because we want the SecurityContext object injected
        // into the factory. then, we extract the principal from
        // that object to return to the caller
        bindFactory(AeroDevicePrincipalFactory.class).to(AeroDevicePrincipal.class);
    }

    private static final class AeroDevicePrincipalFactory implements Factory<AeroDevicePrincipal> {

        private final SecurityContext securityContext;

        // inject the security context instance associated with this request
        @Inject
        AeroDevicePrincipalFactory(SecurityContext securityContext) {
            this.securityContext = securityContext;
        }

        @Override
        public AeroDevicePrincipal provide() {
            Principal principal = securityContext.getUserPrincipal();

            if (principal == null) {
                return null;
            } else {
                Preconditions.checkArgument(principal instanceof AeroDevicePrincipal, "principal is %s instead of an AeroPrincipal", principal.getClass().getSimpleName());
                return (AeroDevicePrincipal) principal;
            }
        }

        @Override
        public void dispose(AeroDevicePrincipal instance) {
            // nothing to be done here
        }
    }
}
