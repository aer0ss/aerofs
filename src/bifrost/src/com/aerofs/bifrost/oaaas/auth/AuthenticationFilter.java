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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.aerofs.restless.jersey.ServletFilter;
import com.google.common.collect.Sets;
import com.sun.jersey.spi.container.ContainerRequest;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aerofs.bifrost.oaaas.auth.OAuth2Validator.ValidationResponse;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;

public class AuthenticationFilter extends ServletFilter
{
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Inject
    private AbstractAuthenticator authenticator;

    @Inject
    private AuthorizationRequestRepository authorizationRequestRepository;

    @Inject
    private OAuth2Validator oAuth2Validator;

    public AuthenticationFilter()
    {
        super("authorize");
    }

    @Override
    public ContainerRequest doFilter(ContainerRequest request) {
        /*
         * Create an authorizationRequest from the request parameters.
         * This can be either a valid or an invalid request, which will be determined by the oAuth2Validator.
         */
        AuthorizationRequest authorizationRequest = extractAuthorizationRequest(request);
        final ValidationResponse validationResponse = oAuth2Validator.validate(authorizationRequest);

        if (authenticator.canCommence(request)) {
            /*
             * Ok, the authenticator wants to have control again (because he stepped
             * out)
             */
            return authenticator.filter(request);
        } else if (validationResponse.valid()) {
            // Request contains correct parameters to be a real OAuth2 request.
            handleInitialRequest(authorizationRequest, request);
            return authenticator.filter(request);
        } else {
            // not an initial request but authentication module cannot handle it either
            throw error(authorizationRequest, validationResponse);
        }
    }

    protected AuthorizationRequest extractAuthorizationRequest(ContainerRequest request) {
        MultivaluedMap<String, String> params = request.getQueryParameters();
        String responseType = params.getFirst("response_type");
        String clientId = params.getFirst("client_id");
        String redirectUri = params.getFirst("redirect_uri");

        List<String> scopes = params.get("scope");
        Set<String> requestedScopes = scopes != null
                ? Sets.newHashSet(scopes) : Collections.<String>emptySet();

        String state = params.getFirst("state");
        String authState = getAuthStateValue();

        return new AuthorizationRequest(responseType, clientId, redirectUri, requestedScopes, state, authState);
    }

    private boolean handleInitialRequest(AuthorizationRequest authReq, ContainerRequest request) {

        try {
            authorizationRequestRepository.save(authReq);
        } catch (Exception e) {
            LOG.error("while saving authorization request", e);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }

        request.getProperties().put(AbstractAuthenticator.AUTH_STATE, authReq.getAuthState());
        request.getProperties().put(AbstractAuthenticator.RETURN_URI, request.getPath());
        return true;
    }

    protected String getAuthStateValue() {
        return UUID.randomUUID().toString();
    }

    private WebApplicationException error(AuthorizationRequest authReq, ValidationResponse validate) {
        LOG.info("Will send error response for authorization request '{}', validation result: {}", authReq, validate);
        String redirectUri = authReq.getRedirectUri();
        String state = authReq.getState();
        if (isValidUrl(redirectUri)) {
            redirectUri = redirectUri.concat(redirectUri.contains("?") ? "&" : "?");
            redirectUri = redirectUri
                    .concat("error=").concat(validate.getValue())
                    .concat("&error_description=").concat(encodeError(validate.getDescription()))
                    .concat(StringUtils.isBlank(state) ? "" : "&state=".concat(state));
            LOG.info("Sending error response, a redirect to: {}", redirectUri);
            return new WebApplicationException(
                    Response.status(302)
                            .location(URI.create(redirectUri))
                            .build());
        } else {
            LOG.info("Sending error response 'bad request': {}", validate.getDescription());
            return new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(validate.getDescription())
                            .build());
        }
    }

    private String encodeError(String msg)
    {
        try {
            return URLEncoder.encode("UTF-8", msg);
        } catch (UnsupportedEncodingException e) {
            return "Unknown";
        }
    }

    public static boolean isValidUrl(String redirectUri) {
        try {
            new URL(redirectUri);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
