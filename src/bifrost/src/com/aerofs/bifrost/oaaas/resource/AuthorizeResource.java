/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.oaaas.resource;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.OAuthScopeParsingUtil;
import com.aerofs.oauth.PrincipalFactory;
import com.google.common.collect.Sets;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Set;

/**
 * Resource for handling authorization codes
 */
@Path("/authorize")
public class AuthorizeResource
{
    @Inject
    private PrincipalFactory _principalFactory;

    @Inject
    private ClientRepository clientRepository;

    @Inject
    private SessionFactory sessionFactory;

    @Inject
    private AuthorizationRequestRepository authorizationRequestRepository;

    private static Logger l = LoggerFactory.getLogger(AuthorizeResource.class);

    /*
     * This is a non-documented API endpoint.
     *
     * Following params are expected:
     *  client_id
     *  redirect_uri
     *  state
     *  response_type
     *  nonce
     *  scope
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response createAuthorization(final MultivaluedMap<String, String> formParameters)
    {
        try {
            l.debug("POST /authorize {}", formParameters);

            Response errorResponse = validateCreateAuthorizationParams(formParameters);
            if (errorResponse != null) {
                return errorResponse;
            }

            String clientId = formParameters.getFirst("client_id");
            Client client = clientRepository.findByClientId(clientId);
            String redirectUri = formParameters.getFirst("redirect_uri");
            String state = formParameters.getFirst("state");
            String responseType = formParameters.getFirst("response_type");
            String nonce = formParameters.getFirst("nonce");
            Set<String> scopes = Sets.newHashSet(formParameters.getFirst("scope").split(","));

            AuthenticatedPrincipal principal;
            try {
                principal = _principalFactory.authenticate(nonce, "todo", scopes);
            } catch (Exception e) {
                l.warn("tried to authenticate nonce {}, got exception {}", nonce, e.toString());
                return sendErrorToRedirectUri(redirectUri, "invalid_request",
                        "proof-of-identity nonce is invalid", state);
            }

            AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseType,
                    client, redirectUri, scopes, state, principal);
            authorizationRequest.setGrantedScopes(scopes);

            // we do a flush here so that if it fails, it is caught by the catch block below.
            // otherwise, a failure would not cause a socket disconnect instead of a 500 response.
            authorizationRequestRepository.save(authorizationRequest);
            sessionFactory.getCurrentSession().flush();

            return sendCodeToRedirectUri(redirectUri, authorizationRequest.getAuthorizationCode(),
                    state);

        } catch (Exception e) {
            l.error(e.toString());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Internal Server Error")
                    .build();
        }
    }

    /**
     * Validate formParameters passed to createAuthorization()
     *
     * @param formParameters POST form parameters
     * @return Error response if error should be returned, or null on success
     */
    private Response validateCreateAuthorizationParams(
            final MultivaluedMap<String, String> formParameters)
            throws UnsupportedEncodingException
    {
        String clientId = formParameters.getFirst("client_id");
        if (clientId == null) {
            l.warn("not specified: client_id");
            return Response.status(Status.BAD_REQUEST)
                    .entity("not specified: client_id")
                    .build();
        }

        Client client = clientRepository.findByClientId(clientId);
        if (client == null) {
            l.warn("no client found with client_id: {}", clientId);
            return Response.status(Status.BAD_REQUEST)
                    .entity("no client found with client_id: " + clientId)
                    .build();
        }

        String redirectUri = formParameters.getFirst("redirect_uri");
        if (redirectUri == null) {
            l.warn("not specified: redirect_uri");
            return Response.status(Status.BAD_REQUEST)
                    .entity("not specified: redirect_uri")
                    .build();
        }
        if (!client.getRedirectUris().get(0).equals(redirectUri)) {
            l.warn("specified redirect uri {} does not match {}",
                    redirectUri,
                    client.getRedirectUris().get(0));
            return Response.status(Status.BAD_REQUEST)
                    .entity("redirect_uri does not match what was specified during client registration")
                    .build();
        }

        // if we get here, then the redirect_uri is valid, and further
        // errors should be sent there

        String state = formParameters.getFirst("state");

        String responseType = formParameters.getFirst("response_type");
        if (responseType == null) {
            l.warn("not specified: response_type");
            return sendErrorToRedirectUri(redirectUri, "invalid_request",
                    "not specified: response_type", state);
        }
        if (!responseType.equals("code")) {
            l.warn("reponse_type must be \"code\", not {}", responseType);
            return sendErrorToRedirectUri(redirectUri, "unsupported_response_type",
                    "response type must be \"code\"", state);
        }

        String nonce = formParameters.getFirst("nonce");
        if (nonce == null) {
            l.warn("not specified: nonce");
            return sendErrorToRedirectUri(redirectUri, "invalid_request",
                    "not specified: nonce", state);
        }

        String scope = formParameters.getFirst("scope");
        if (scope == null) {
            l.warn("not specified: scope");
            return sendErrorToRedirectUri(redirectUri, "invalid_request",
                    "not specified: scope", state);
        }
        try {
            OAuthScopeParsingUtil.validateScopes(formParameters.getFirst("scope"));
        } catch (ExFormatError e) {
            l.warn("invalid scope: ", formParameters.getFirst("scope"));
            return sendErrorToRedirectUri(redirectUri, "invalid_request",
                    "invalid scope", state);
        }

        return null;
    }

    private Response sendErrorToRedirectUri(String redirectUri, String error,
            @Nullable String errorDescription, @Nullable String state)
            throws UnsupportedEncodingException
    {
        String sep = redirectUri.contains("?") ? "&" : "?";
        String location = redirectUri + sep + "error=" + URLEncoder.encode(error, "UTF-8");
        if (errorDescription != null) {
            location = location + "&error_description=" + URLEncoder.encode(errorDescription, "UTF-8");
        }
        if (state != null) {
            location = location + "&state=" + URLEncoder.encode(state, "UTF-8");
        }
        return Response.status(302)
                .header("Location", location)
                .build();
    }

    private Response sendCodeToRedirectUri(String redirectUri, String code, @Nullable String state)
            throws UnsupportedEncodingException
    {
        String sep = redirectUri.contains("?") ? "&" : "?";
        String location = redirectUri + sep + "code=" + URLEncoder.encode(code, "UTF-8");
        if (state != null) {
            location = location + "&state=" + URLEncoder.encode(state, "UTF-8");
        }
        return Response.status(302)
                .header("Location", location)
                .build();
    }
}
