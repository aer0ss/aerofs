/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.aerofs.bifrost.oaaas.resource;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.ids.UniqueID;
import com.aerofs.bifrost.oaaas.auth.MobileDeviceManagement;
import com.aerofs.bifrost.oaaas.auth.OAuth2Validator;
import com.aerofs.bifrost.oaaas.auth.ValidationResponseException;
import com.aerofs.bifrost.oaaas.auth.principal.UserPassCredentials;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.model.AccessTokenRequest;
import com.aerofs.bifrost.oaaas.model.AccessTokenResponse;
import com.aerofs.bifrost.oaaas.model.AuthorizationRequest;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ErrorResponse;
import com.aerofs.bifrost.oaaas.model.TokenListReponse;
import com.aerofs.bifrost.oaaas.model.TokenResponseObject;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;
import com.aerofs.bifrost.oaaas.repository.AuthorizationRequestRepository;
import com.aerofs.bifrost.oaaas.repository.ClientRepository;
import com.aerofs.bifrost.server.Transactional;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.OAuthScopeParsingUtil;
import com.aerofs.oauth.PrincipalFactory;
import com.aerofs.rest.auth.PrivilegedServiceToken;
import com.aerofs.restless.Auth;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.BEARER;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.GRANT_TYPE_AUTHORIZATION_CODE;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.GRANT_TYPE_REFRESH_TOKEN;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.ValidationResponse;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.ValidationResponse.UNKNOWN_CLIENT_ID;

/**
 * Resource for handling all calls related to tokens. It adheres to <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2"> the OAuth spec</a>.
 */
@Path("/")
@Transactional
public class TokenResource
{
    public static final String X_REAL_IP = "X-Real-IP";

    public static final String BASIC_REALM = "Basic realm=\"OAuth2 Secure\"";

    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private static Logger l = LoggerFactory.getLogger(TokenResource.class);

    @Inject
    private AuthorizationRequestRepository authorizationRequestRepository;

    @Inject
    private AccessTokenRepository accessTokenRepository;

    @Inject
    private OAuth2Validator oAuth2Validator;

    @Inject
    private ClientRepository clientRepository;

    @Inject
    private PrincipalFactory _principalFactory;

    /**
     * List tokens issued on behalf of the specified user.
     */
    @GET
    @Path("/users/{owner}/tokens")
    @Transactional(readOnly = true)
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTokens(@Auth PrivilegedServiceToken authToken, @PathParam("owner") String owner)
    {
        try {
            l.info("listing access tokens for {}", owner);

            List<AccessToken> tokens = accessTokenRepository.findByOwner(owner);

            List<TokenResponseObject> tokenResponseObjects =
                    new ArrayList<>(tokens.size());
            for (AccessToken t : tokens) {
                tokenResponseObjects.add(new TokenResponseObject(t));
            }

            TokenListReponse response = new TokenListReponse(tokenResponseObjects);
            return Response.ok(response).build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.serverError().build();
        }
    }

    /**
     * Delete all personal tokens issued by this user
     */
    @DELETE
    @Path("/users/{owner}/tokens")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAllTokens(@Auth PrivilegedServiceToken authToken, @PathParam("owner") String owner)
    {
        try {
            l.info("delete all tokens for owner {}", owner);
            accessTokenRepository.deleteAllTokensByOwner(owner);
            return Response.ok().build();
        } catch (Exception e) {
            l.error(e.toString());
            return Response.serverError().build();
        }
    }

    /**
     * Delete delegated tokens issued by this (probably admin) user
     */
    @DELETE
    @Path("/users/{owner}/delegates")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAdminTokens(@Auth PrivilegedServiceToken authToken, @PathParam("owner") String owner)
    {
        try {
            l.info("delete admin tokens for owner {}", owner);
            accessTokenRepository.deleteDelegatedTokensByOwner(owner);
            return Response.ok().build();
        } catch (Exception e) {
            l.error(e.toString());
            return Response.serverError().build();
        }
    }

    private static boolean hasMobileClientId(String clientID) {
        return clientID.equals("aerofs-android") || clientID.equals("aerofs-ios");
    }

    /**
     * A client would like to grant a new token to an app with parameters as specified in formParameters
     */
    @POST
    @Path("/token")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/x-www-form-urlencoded")
    public Response token(@HeaderParam("Authorization") String authorization,
            final MultivaluedMap<String, String> formParameters, @Context HttpHeaders headers)
    {
        AccessTokenRequest accessTokenRequest = AccessTokenRequest.fromMultiValuedFormParameters(
                formParameters);
        UserPassCredentials credentials = getClientCredentials(authorization, accessTokenRequest);
        String grantType = accessTokenRequest.getGrantType();

        if(MobileDeviceManagement.isMDMEnabled() && hasMobileClientId(accessTokenRequest.getClientId())) {
            List<String> realIPVals = headers.getRequestHeader(X_REAL_IP);
            if (realIPVals == null) {
                l.error("denied mobile client's create token request with missing IP, should have been set by nginx");
                return sendErrorResponse(ValidationResponse.MISSING_X_REAL_IP);
            }
            String remoteIP = realIPVals.get(0);
            l.debug("got a mobile token request from real ip: {}", remoteIP);
            if (!MobileDeviceManagement.isWhitelistedIP(remoteIP)) {
                l.info("denied mobile client in create token request from non-whitelisted IP: {}",
                        remoteIP);
                return sendErrorResponse(ValidationResponse.FAIL_IP_WHITELIST);
            }
            l.info("mobile client create token request from whitelisted IP: {}", remoteIP);
        }

        ValidationResponse vr = oAuth2Validator.validate(accessTokenRequest);
        if (!vr.valid()) {
            l.warn("validation error in create token request: {}", vr.getValue());
            return sendErrorResponse(vr);
        }
        AuthorizationRequest request;
        try {
            if (GRANT_TYPE_AUTHORIZATION_CODE.equals(grantType)) {
                request = authRequestFromCode(accessTokenRequest);
            } else if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
                request = authRequestFromRefreshToken(accessTokenRequest);
            } else {
                return sendErrorResponse(ValidationResponse.UNSUPPORTED_GRANT_TYPE);
            }
        } catch (ValidationResponseException e) {
            l.warn("validation error in create token request", e.v);
            return sendErrorResponse(e.v);
        }
        if (!request.getClient().isExactMatch(credentials)) {
            l.warn("invalid client credentials in create token request");
            // FIXME why do we send this reponse? what do these headers do? it's unlike a normal oauth error response
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header(WWW_AUTHENTICATE, BASIC_REALM)
                    .build();
        }
        AccessToken token = createAccessToken(request, false);
        l.info("created token {} for {}", token.getToken(), token.getClientId());

        AccessTokenResponse response = new AccessTokenResponse(
                token.getToken(),
                BEARER,
                Objects.firstNonNull(request.expiresInSeconds, request.getClient().getExpireDuration()),
                token.getRefreshToken(),
                StringUtils.join(token.getScopes(), ','));

        return Response.ok().entity(response).build();
    }

    /**
     * Delete a token.  No auth is needed, as possession of the token is proof of authorization.
     */
    @DELETE
    @Path("/token/{token}")
    public Response deleteToken(@PathParam("token") String token)
    {
        try {
            l.info("delete token {}", token);
            AccessToken accessToken = accessTokenRepository.findByToken(token);

            if (accessToken == null) {
                l.info("token not found: {}", token);
                return Response.status(Status.NOT_FOUND).build();
            }

            accessTokenRepository.delete(accessToken);

            return Response.ok().build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.serverError().build();
        }
    }

    /**
     * http://tools.ietf.org/html/draft-ietf-oauth-v2#section-2.3.1
     *
     * We support both options. Clients can use the Basic Authentication or
     * include the secret and id in the request body
     */
    private UserPassCredentials getClientCredentials(String authorization,
            AccessTokenRequest accessTokenRequest)
    {
        return StringUtils.isBlank(authorization) ? new UserPassCredentials(
                accessTokenRequest.getClientId(),
                accessTokenRequest.getClientSecret()) : new UserPassCredentials(authorization);
    }

    private AuthorizationRequest authRequestFromRefreshToken(AccessTokenRequest accessTokenRequest)
    {
        AccessToken accessToken = accessTokenRepository.findByRefreshToken(
                accessTokenRequest.getRefreshToken());
        if (accessToken == null) {
            throw new ValidationResponseException(ValidationResponse.INVALID_GRANT_REFRESH_TOKEN);
        }
        AuthorizationRequest request = new AuthorizationRequest();
        request.setClient(accessToken.getClient());
        request.setPrincipal(accessToken.getPrincipal());
        request.setGrantedScopes(accessToken.getScopes());
        accessTokenRepository.delete(accessToken);
        return request;

    }

    /**
     * Fetch the AuthorizationRequest from the DB if the user provided an authorization code. If
     * the user provided an SP nonce, fetch the principal information from SP and create an
     * AuthorizationRequest object.
     */
    private AuthorizationRequest authRequestFromCode(AccessTokenRequest accessTokenRequest)
    {
        return (accessTokenRequest.hasDeviceAuthorizationNonce()) ?
                authRequestFromNonce(accessTokenRequest)
                : authRequestFromAuthCode(accessTokenRequest);
    }

    /**
     * Authorize a token request that arrives with a device authorization nonce.
     * In this case, we don't have an access code record to look up, therefore we have to
     * get the principal information (it comes from SP via getDeviceAuthorization).
     */
    private AuthorizationRequest authRequestFromNonce(AccessTokenRequest accessTokenRequest)
    {
        AuthenticatedPrincipal principal;
        Client client = getClientForRequest(accessTokenRequest);

        Set<String> scopes = accessTokenRequest.getScope() == null ?
            client.getScopes() : Sets.newHashSet(accessTokenRequest.getScope().split(","));

        try {
            OAuthScopeParsingUtil.validateScopes(scopes);
        } catch (ExFormatError e) {
            l.info("Invalid scope: {}", scopes);
            throw new ValidationResponseException(ValidationResponse.SCOPE_NOT_VALID);
        }

        try {
            principal = _principalFactory.authenticate(
                    accessTokenRequest.getDeviceAuthorizationNonce(), "todo", scopes);
        } catch (Exception e) {
            l.info("Error handling device authorization nonce {}.",
                    accessTokenRequest.getDeviceAuthorizationNonce(), LogUtil.suppress(e));
            throw new ValidationResponseException(
                    ValidationResponse.INVALID_GRANT_AUTHORIZATION_CODE);
        }

        AuthorizationRequest authReq = new AuthorizationRequest();
        authReq.setPrincipal(principal);
        authReq.setClient(client);
        authReq.setGrantedScopes(scopes);

        if (accessTokenRequest.getExpiresInSeconds() != null) {
            if (client.getExpireDuration() == 0) {
                // if the client allows unlimited duration, give the requester what he asks for
                authReq.expiresInSeconds = accessTokenRequest.getExpiresInSeconds();
            } else if (accessTokenRequest.getExpiresInSeconds() == 0) {
                // if the requester asks for unlimited duration, give the client maximum
                authReq.expiresInSeconds = client.getExpireDuration();
            } else {
                // otherwise, give the requester what he asks for, up to the client maximum
                authReq.expiresInSeconds = Math.min(
                        accessTokenRequest.getExpiresInSeconds(),
                        client.getExpireDuration());
            }
        }

        String uri = accessTokenRequest.getRedirectUri();
        if (uri != null && (!uri.equalsIgnoreCase(authReq.getRedirectUri()))) {
            throw new ValidationResponseException(ValidationResponse.REDIRECT_URI_DIFFERENT);
        }

        return authReq;
    }

    /**
     * Authorize a request that arrives with an OAuth access code. In this case, the principal
     * information comes from the original authorization request (stored in the db when
     * the access code was granted)
     */
    private AuthorizationRequest authRequestFromAuthCode(AccessTokenRequest accessTokenRequest)
    {
        AuthorizationRequest authReq = authorizationRequestRepository.findByAuthorizationCode(
                accessTokenRequest.getCode());
        if (authReq == null) {
            l.info("Error handling access code {}.", accessTokenRequest.getCode());
            throw new ValidationResponseException(ValidationResponse.INVALID_GRANT_AUTHORIZATION_CODE);
        }
        String uri = accessTokenRequest.getRedirectUri();
        if (!authReq.getRedirectUri().equalsIgnoreCase(uri)) {
            throw new ValidationResponseException(ValidationResponse.REDIRECT_URI_DIFFERENT);
        }
        authorizationRequestRepository.delete(authReq);
        return authReq;
    }

    /**
     * @throws ValidationResponseException if the client ID is missing or does not match a client
     */
    private @NotNull Client getClientForRequest(AccessTokenRequest accessTokenRequest)
    {
        Client client = StringUtils.isBlank(accessTokenRequest.getClientId()) ?
                null : clientRepository.findByClientId(accessTokenRequest.getClientId());
        if (client == null) {
            throw new ValidationResponseException(UNKNOWN_CLIENT_ID);
        }
        return client;
    }

    private AccessToken createAccessToken(AuthorizationRequest request, boolean isImplicitGrant)
    {
        Client client = request.getClient();
        long expireDurationSeconds =
                Objects.firstNonNull(request.expiresInSeconds, client.getExpireDuration());
        long expiresMilli = expireDurationSeconds == 0 ?
                0 : System.currentTimeMillis() + (1000 * expireDurationSeconds);
        String refreshToken = (client.isUseRefreshTokens() && !isImplicitGrant) ?
                newTokenValue() : null;
        AuthenticatedPrincipal principal = request.getPrincipal();
        return accessTokenRepository.save(
                new AccessToken(
                        newTokenValue(),
                        principal,
                        client,
                        expiresMilli,
                        request.getGrantedScopes(),
                        refreshToken)
        );
    }

    /**
     * Generate a new access token or refresh token string
     */
    private String newTokenValue()
    {
        return UniqueID.generate().toStringFormal();
    }

    private Response sendErrorResponse(String error, String description)
    {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(error, description))
                .build();
    }

    private Response sendErrorResponse(ValidationResponse response)
    {
        return sendErrorResponse(response.getValue(), response.getDescription());
    }
}
