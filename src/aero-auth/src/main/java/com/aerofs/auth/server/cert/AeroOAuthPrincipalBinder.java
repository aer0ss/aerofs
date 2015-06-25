package com.aerofs.auth.server.cert;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.AeroPrincipal;
import com.aerofs.auth.server.PrincipalFactory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

@Provider
public class AeroOAuthPrincipalBinder extends AbstractBinder
{
    private static final class ActualPrincipalFactory extends PrincipalFactory<AeroPrincipal> {

        @Inject
        public ActualPrincipalFactory(SecurityContext securityContext) {
            super(securityContext);
        }
    }

    @Override
    protected void configure() {
        bindFactory(ActualPrincipalFactory.class).to(AeroOAuthPrincipal.class);
    }
}
