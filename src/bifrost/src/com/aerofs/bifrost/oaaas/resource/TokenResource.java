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

import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.bifrost.core.URLConnectionConfigurator;
import com.aerofs.bifrost.oaaas.auth.AbstractAuthenticator;
import com.aerofs.bifrost.oaaas.auth.AbstractUserConsentHandler;
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
import com.aerofs.lib.log.LogUtil;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.sun.jersey.api.core.HttpContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.BEARER;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.GRANT_TYPE_AUTHORIZATION_CODE;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.GRANT_TYPE_CLIENT_CREDENTIALS;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.GRANT_TYPE_REFRESH_TOKEN;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.ValidationResponse;
import static com.aerofs.bifrost.oaaas.auth.OAuth2Validator.ValidationResponse.UNKNOWN_CLIENT_ID;

/**
 * Resource for handling all calls related to tokens. It adheres to <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2"> the OAuth spec</a>.
 */
@Path("/")
public class TokenResource
{

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
    private SPBlockingClient.Factory spFactory;

    private static final Logger LOG = LoggerFactory.getLogger(TokenResource.class);

    @GET
    @Path("/authorize")
    public Response authorizeCallbackGet(@Context HttpContext context)
    {
        return authorizeCallback(context);
    }

    /**
     * Entry point for the authorize call which needs to return an authorization code or (implicit
     * grant) an access token
     *
     * @return Response the response
     */
    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/authorize")
    public Response authorizeCallback(@Context HttpContext context)
    {
        return doProcess(context);
    }

    /**
     * Called after the user has given consent
     *
     * @return Response the response
     */
    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/consent")
    public Response consentCallback(@Context HttpContext context)
    {
        return doProcess(context);
    }

    private Response doProcess(HttpContext request)
    {
        AuthorizationRequest authReq = findAuthorizationRequest(request);
        if (authReq == null) {
            return serverError("No valid auth_state in the Request");
        }
        processScopes(authReq, request);
        if (authReq.getResponseType().equals(OAuth2Validator.IMPLICIT_GRANT_RESPONSE_TYPE)) {
            AccessToken token = createAccessToken(authReq, true);
            return sendImplicitGrantResponse(authReq, token);
        } else {
            return sendAuthorizationCodeResponse(authReq);
        }
    }

    /*
     * In the user consent filter the scopes are (possible) set on the Request
     */
    @SuppressWarnings("unchecked")
    private void processScopes(AuthorizationRequest authReq, HttpContext context)
    {
        if (authReq.getClient().isSkipConsent()) {
            // return the scopes in the authentication request since the requested scopes are stored in the
            // authorizationRequest.
            authReq.setGrantedScopes(authReq.getRequestedScopes());
        } else {
            Set<String> scopes = (Set<String>)context.getProperties()
                    .get(AbstractUserConsentHandler.GRANTED_SCOPES);
            authReq.setGrantedScopes(scopes != null && !scopes.isEmpty() ? scopes : null);
        }
    }

    private AccessToken createAccessToken(AuthorizationRequest request, boolean isImplicitGrant)
    {
        Client client = request.getClient();
        long expireDuration = client.getExpireDuration();
        long expires = (
                expireDuration == 0L ? 0L : (System.currentTimeMillis() + (1000 * expireDuration)));
        String refreshToken = (client.isUseRefreshTokens() && !isImplicitGrant) ? getTokenValue(
                true) : null;
        AuthenticatedPrincipal principal = request.getPrincipal();
        AccessToken token = new AccessToken(getTokenValue(false), principal, client, expires,
                request.getGrantedScopes(), refreshToken);
        return accessTokenRepository.save(token);
    }

    private AuthorizationRequest findAuthorizationRequest(HttpContext context)
    {
        String authState = (String)context.getProperties().get(AbstractAuthenticator.AUTH_STATE);
        return authorizationRequestRepository.findByAuthState(authState);
    }

    @GET
    @Path("/tokenlist")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTokens(@QueryParam("owner") String owner)
    {
        try {
            if (owner == null) {
                l.warn("GET /tokenlist with no owner specified");
                return Response.status(Status.BAD_REQUEST).build();
            }

            l.info("listing access tokens for {}", owner);

            List<AccessToken> tokens = accessTokenRepository.findByOwner(owner);

            List<TokenResponseObject> tokenResponseObjects =
                    new ArrayList<TokenResponseObject>(tokens.size());
            for (AccessToken t : tokens) {
                tokenResponseObjects.add(new TokenResponseObject(
                        t.getClientId(),
                        t.getClient().getName(),
                        t.getCreationDate(),
                        t.getExpires(),
                        t.getToken()));
            }

            TokenListReponse response = new TokenListReponse(tokenResponseObjects);
            return Response.ok(response).build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.serverError().build();
        }
    }

    @DELETE
    @Path("/token/{token}")
    public Response deleteToken(@PathParam("token") String token)
    {
        try {
            AccessToken accessToken = accessTokenRepository.findByToken(token);

            if (accessToken == null) {
                l.warn("token not found: {}", token);
                return Response.status(Status.NOT_FOUND).build();
            }

            accessTokenRepository.delete(accessToken);

            return Response.ok().build();

        } catch (Exception e) {
            l.error(e.toString());
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/token")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/x-www-form-urlencoded")
    public Response token(@HeaderParam("Authorization") String authorization,
            final MultivaluedMap<String, String> formParameters)
    {
        AccessTokenRequest accessTokenRequest = AccessTokenRequest.fromMultiValuedFormParameters(
                formParameters);
        UserPassCredentials credentials = getUserPassCredentials(authorization, accessTokenRequest);
        String grantType = accessTokenRequest.getGrantType();
        if (GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
            accessTokenRequest.setClientId(credentials.getUsername());
        }
        ValidationResponse vr = oAuth2Validator.validate(accessTokenRequest);
        if (!vr.valid()) {
            return sendErrorResponse(vr);
        }
        AuthorizationRequest request;
        try {
            if (GRANT_TYPE_AUTHORIZATION_CODE.equals(grantType)) {
                request = authorizationCodeToken(accessTokenRequest);
            } else if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
                request = refreshTokenToken(accessTokenRequest);
            } else if (GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
                request = new AuthorizationRequest();
                request.setClient(accessTokenRequest.getClient());
                // We have to construct a AuthenticatedPrincipal on-the-fly as there is only key-secret authentication
                request.setPrincipal(new AuthenticatedPrincipal(request.getClient().getClientId()));
                // Apply all client scopes to the access token.
                // TODO: take into account given scopes from the request
                request.setGrantedScopes(request.getClient().getScopes());
            } else {
                return sendErrorResponse(ValidationResponse.UNSUPPORTED_GRANT_TYPE);
            }
        } catch (ValidationResponseException e) {
            return sendErrorResponse(e.v);
        }
        if (!request.getClient().isExactMatch(credentials)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header(WWW_AUTHENTICATE, BASIC_REALM)
                    .build();
        }
        AccessToken token = createAccessToken(request, false);

        AccessTokenResponse response = new AccessTokenResponse(token.getToken(), BEARER,
                request.getClient().getExpireDuration(), token.getRefreshToken(),
                StringUtils.join(token.getScopes(), ','));

        return Response.ok().entity(response).build();

    }

    private AuthorizationRequest authorizationCodeToken(AccessTokenRequest accessTokenRequest)
    {
        return (accessTokenRequest.hasDeviceAuthorizationNonce()) ? handleDeviceAuthorization(
                accessTokenRequest) : handleAccessCode(accessTokenRequest);
    }

    private AuthorizationRequest handleDeviceAuthorization(AccessTokenRequest accessTokenRequest)
    {
        AuthenticatedPrincipal principal;
        try {
            principal = getDeviceAuthorization(accessTokenRequest);
        } catch (Exception e) {
            l.info("Error handling device authorization nonce {}.",
                    accessTokenRequest.getDeviceAuthorizationNonce(), LogUtil.suppress(e));
            throw new ValidationResponseException(
                    ValidationResponse.INVALID_GRANT_AUTHORIZATION_CODE);
        }

        AuthorizationRequest authReq = new AuthorizationRequest();
        Client client = getClientForRequest(accessTokenRequest);
        authReq.setPrincipal(principal);
        authReq.setClient(client);
        authReq.setGrantedScopes(client.getScopes());

        String uri = accessTokenRequest.getRedirectUri();
        if (uri != null && (!authReq.getRedirectUri().equalsIgnoreCase(uri))) {
            throw new ValidationResponseException(ValidationResponse.REDIRECT_URI_DIFFERENT);
        }

        return authReq;
    }

    private Client getClientForRequest(AccessTokenRequest accessTokenRequest)
    {
        Client client = StringUtils.isBlank(
                accessTokenRequest.getClientId()) ? null : clientRepository.findByClientId(
                accessTokenRequest.getClientId());
        if (client == null) {
            throw new ValidationResponseException(UNKNOWN_CLIENT_ID);
        }
        return client;
    }

    private AuthorizationRequest handleAccessCode(AccessTokenRequest accessTokenRequest)
    {
        AuthorizationRequest authReq = authorizationRequestRepository.findByAuthorizationCode(
                accessTokenRequest.getCode());
        if (authReq == null) {
            l.info("Error handling access code {}.", accessTokenRequest.getCode());
            throw new ValidationResponseException(
                    ValidationResponse.INVALID_GRANT_AUTHORIZATION_CODE);
        }
        String uri = accessTokenRequest.getRedirectUri();
        if (!authReq.getRedirectUri().equalsIgnoreCase(uri)) {
            throw new ValidationResponseException(ValidationResponse.REDIRECT_URI_DIFFERENT);
        }
        authorizationRequestRepository.delete(authReq);
        return authReq;
    }

    private AuthenticatedPrincipal getDeviceAuthorization(AccessTokenRequest tokenRequest)
            throws Exception
    {
        SPBlockingClient client = spFactory.create_(
                URLConnectionConfigurator.CONNECTION_CONFIGURATOR);

        AuthorizeMobileDeviceReply authReply = client.authorizeMobileDevice(
                tokenRequest.getDeviceAuthorizationNonce(), "todo");

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal();
        principal.setName(authReply.getUserId());

        principal.setUserID(UserID.fromExternal(authReply.getUserId()));
        principal.setOrganizationID(new OrganizationID(Integer.valueOf(authReply.getOrgId())));
        principal.setAdminPrincipal(authReply.getIsOrgAdmin());

        return principal;
    }

    private AuthorizationRequest refreshTokenToken(AccessTokenRequest accessTokenRequest)
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

  /*
   * http://tools.ietf.org/html/draft-ietf-oauth-v2#section-2.3.1
   *
   * We support both options. Clients can use the Basic Authentication or
   * include the secret and id in the request body
   */

    private UserPassCredentials getUserPassCredentials(String authorization,
            AccessTokenRequest accessTokenRequest)
    {
        return StringUtils.isBlank(authorization) ? new UserPassCredentials(
                accessTokenRequest.getClientId(),
                accessTokenRequest.getClientSecret()) : new UserPassCredentials(authorization);
    }

    private Response sendAuthorizationCodeResponse(AuthorizationRequest authReq)
    {
        String uri = authReq.getRedirectUri();
        String authorizationCode = getAuthorizationCodeValue();
        authReq.setAuthorizationCode(authorizationCode);
        authorizationRequestRepository.save(authReq);
        uri = uri + appendQueryMark(uri) + "code=" + authorizationCode +
                appendStateParameter(authReq);
        return Response.seeOther(UriBuilder.fromUri(uri).build()).build();
    }

    protected String getTokenValue(boolean isRefreshToken)
    {
        return UniqueID.generate().toStringFormal();
    }

    protected String getAuthorizationCodeValue()
    {
        return getTokenValue(false);
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

    private Response sendImplicitGrantResponse(AuthorizationRequest authReq,
            AccessToken accessToken)
    {
        String uri = authReq.getRedirectUri();
        String fragment = String.format("access_token=%s&token_type=bearer&expires_in=%s&scope=%s" +
                appendStateParameter(authReq), accessToken.getToken(), accessToken.getExpires(),
                StringUtils.join(authReq.getGrantedScopes(), ','));
        if (authReq.getClient().isIncludePrincipal()) {
            fragment += String.format("&principal=%s", authReq.getPrincipal().getDisplayName());
        }
        return Response.seeOther(UriBuilder.fromUri(uri).fragment(fragment).build()).build();
    }

    private String appendQueryMark(String uri)
    {
        return uri.contains("?") ? "&" : "?";
    }

    private String appendStateParameter(AuthorizationRequest authReq)
    {
        String state = authReq.getState();
        return StringUtils.isBlank(state) ? "" : "&state=".concat(state);
    }

    private Response serverError(String msg)
    {
        LOG.warn(msg);
        return Response.serverError().build();
    }

    /**
     * @param authorizationRequestRepository the authorizationRequestRepository to set
     */
    public void setAuthorizationRequestRepository(
            AuthorizationRequestRepository authorizationRequestRepository)
    {
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    /**
     * @param accessTokenRepository the accessTokenRepository to set
     */
    public void setAccessTokenRepository(AccessTokenRepository accessTokenRepository)
    {
        this.accessTokenRepository = accessTokenRepository;
    }

    /**
     * @param oAuth2Validator the oAuth2Validator to set
     */
    public void setoAuth2Validator(OAuth2Validator oAuth2Validator)
    {
        this.oAuth2Validator = oAuth2Validator;
    }
}
