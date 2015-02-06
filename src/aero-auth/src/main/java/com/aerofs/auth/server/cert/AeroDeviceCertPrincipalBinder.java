package com.aerofs.auth.server.cert;

import com.aerofs.auth.server.PrincipalFactory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * Implementation of an HK2 {@code Binder} that injects
 * {@link AeroDeviceCertPrincipal} instances into {@link javax.ws.rs.core.Context}-
 * annotated JAX-RS objects and methods.
 * <br>
 * An instance of this class should <strong>only</strong> be added
 * as a JAX-RS {@link Provider} to a Jersey application.
 * <br>
 * This implementation depends on the {@link SecurityContext} injector
 * defined by Jersey and which is automatically added to Jersey applications.
 */
@Provider
public final class AeroDeviceCertPrincipalBinder extends AbstractBinder {

    private static final class ActualPrincipalFactory extends PrincipalFactory<AeroDeviceCertPrincipal> {

        @Inject
        public ActualPrincipalFactory(SecurityContext securityContext) {
            super(securityContext);
        }
    }

    @Override
    protected void configure() {
        // note that this is how the Jersey docs recommend we do this
        // see: https://jersey.java.net/documentation/latest/ioc.html
        // create a new instance of the factory every time
        // because we want the SecurityContext object injected
        // into the factory. then, we extract the principal from
        // that object to return to the caller
        bindFactory(ActualPrincipalFactory.class).to(AeroDeviceCertPrincipal.class);
    }
}
