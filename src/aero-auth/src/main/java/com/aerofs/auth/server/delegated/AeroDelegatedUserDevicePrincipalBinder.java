package com.aerofs.auth.server.delegated;

import com.aerofs.auth.server.PrincipalFactory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * Implementation of an HK2 {@code Binder} that injects
 * {@link AeroDelegatedUserDevicePrincipal} instances into
 * {@link javax.ws.rs.core.Context}- annotated JAX-RS
 * objects and methods.
 * <br>
 * An instance of this class should <strong>only</strong> be added
 * as a JAX-RS {@link Provider} to a Jersey application.
 * <br>
 * This implementation depends on the {@link SecurityContext} injector
 * defined by Jersey and which is automatically added to Jersey applications.
 */
@Provider
public final class AeroDelegatedUserDevicePrincipalBinder extends AbstractBinder {

    private static final class ActualPrincipalFactory extends PrincipalFactory<AeroDelegatedUserDevicePrincipal> {

        @Inject
        public ActualPrincipalFactory(SecurityContext securityContext) {
            super(securityContext);
        }
    }


    @Override
    protected void configure() {
        bindFactory(ActualPrincipalFactory.class).to(AeroDelegatedUserDevicePrincipal.class);
    }
}
