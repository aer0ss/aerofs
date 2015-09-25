package com.aerofs.trifrost;

import com.aerofs.trifrost.model.AuthorizedUser;
import com.google.common.base.Preconditions;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;

public final class UserBinder extends AbstractBinder {
    static final Logger l = LoggerFactory.getLogger(UserBinder.class);

    @Override
    protected void configure() {
        bindFactory(AuthorizedUserFactory.class).to(AuthorizedUser.class);
    }

    private static final class AuthorizedUserFactory implements Factory<AuthorizedUser> {
        private final SecurityContext securityContext;

        // inject the security context instance associated with this request
        @Inject
        AuthorizedUserFactory(SecurityContext securityContext) {
            this.securityContext = securityContext;
        }

        @Override
        public AuthorizedUser provide() {
            Preconditions.checkNotNull(securityContext);
            Preconditions.checkNotNull(securityContext.getUserPrincipal());
            String userId = securityContext.getUserPrincipal().getName();

            l.debug("generate authorized user for {}", userId);
            return new AuthorizedUser(userId);
        }

        @Override
        public void dispose(AuthorizedUser instance) { }
    }
}
