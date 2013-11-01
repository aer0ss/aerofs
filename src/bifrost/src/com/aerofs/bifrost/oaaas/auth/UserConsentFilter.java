/*
 * Copyright 2012 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.bifrost.oaaas.auth;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.aerofs.restless.jersey.ServletFilter;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.apache.commons.lang.StringUtils;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;

/**
 *
 * {@link ContainerRequestFilter} that ensures the Resource Owner grants consent for the use of
 * the Resource Server data to the Client app.
 *
 */
public class UserConsentFilter extends ServletFilter
{
    private static final String RETURN_URI = "/oauth2/consent";

    @Inject
    private AuthorizationRequestRepository authorizationRequestRepository;

    @Inject
    private AbstractUserConsentHandler userConsentHandler;

    public UserConsentFilter()
    {
        super("authorize");
    }

    @Override
    public ContainerRequest doFilter(ContainerRequest request) {
        AuthorizationRequest authorizationRequest = findAuthorizationRequest(request);
        if (authorizationRequest == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("No valid auth_state in the Request")
                            .build());
        }
        if (initialRequest(request)) {
            storePrincipal(request, authorizationRequest);
            request.getProperties().put(AbstractAuthenticator.RETURN_URI, RETURN_URI);
            request.getProperties().put(AbstractUserConsentHandler.CLIENT, authorizationRequest.getClient());
            if (!authorizationRequest.getClient().isSkipConsent()) {
                return userConsentHandler.filter(request);
            } else {
                return request;
            }
        } else {
            /*
             * Ok, the consentHandler wants to have control again (because he stepped
             * out)
             */
            return userConsentHandler.filter(request);
        }
    }

    private AuthorizationRequest findAuthorizationRequest(ContainerRequest request) {
        String authState = (String) request.getProperties().get(AbstractAuthenticator.AUTH_STATE);
        if (StringUtils.isBlank(authState)) {
            authState = request.getQueryParameters().getFirst(AbstractAuthenticator.AUTH_STATE);
        }
        return authorizationRequestRepository.findByAuthState(authState);
    }

    private void storePrincipal(ContainerRequest request, AuthorizationRequest authorizationRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) request.getProperties().get(AbstractAuthenticator.PRINCIPAL);
        if (principal == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity("No valid AbstractAuthenticator.PRINCIPAL on the Request")
                    .build());
        }
        authorizationRequest.setPrincipal(principal);
        authorizationRequestRepository.save(authorizationRequest);
    }

    private boolean initialRequest(ContainerRequest request) {
        return (AuthenticatedPrincipal) request.getProperties().get(AbstractAuthenticator.PRINCIPAL) != null;
    }
}
