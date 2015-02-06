package com.aerofs.auth.server;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * Implementation of an HK2 {@code Binder} that injects
 * {@link AeroPrincipal} instances into {@link javax.ws.rs.core.Context}-
 * annotated JAX-RS objects and methods.
 * <br>
 * An instance of this class should <strong>only</strong> be added
 * as a JAX-RS {@link Provider} to a Jersey application.
 * <br>
 * This implementation depends on the {@link SecurityContext} injector
 * defined by Jersey and which is automatically added to Jersey applications.
 */
@Provider
public final class AeroPrincipalBinder extends AbstractBinder {

    private static final class ActualPrincipalFactory extends PrincipalFactory<AeroPrincipal> {

        @Inject
        public ActualPrincipalFactory(SecurityContext securityContext) {
            super(securityContext);
        }
    }

    @Override
    protected void configure() {
        bindFactory(ActualPrincipalFactory.class).to(AeroPrincipal.class);
    }
}
