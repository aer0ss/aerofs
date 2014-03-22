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

import com.aerofs.base.id.UniqueID;
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
import com.aerofs.oauth.PrincipalFactory;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
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
    private PrincipalFactory _principalFactory;

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

    private AccessToken createAccessToken(AuthorizationRequest request, boolean isImplicitGrant)
    {
        Client client = request.getClient();
        long expires = client.getExpireDuration() == 0 ?
                0L : (System.currentTimeMillis() + (1000 * client.getExpireDuration()));
        String refreshToken = (client.isUseRefreshTokens() && !isImplicitGrant) ?
                getTokenValue(true) : null;
        AuthenticatedPrincipal principal = request.getPrincipal();
        return accessTokenRepository.save(
                new AccessToken(
                    getTokenValue(false),
                    principal,
                    client,
                    expires,
                    request.getGrantedScopes(),
                    refreshToken)
        );
    }

    private AuthorizationRequest authorizationCodeToken(AccessTokenRequest accessTokenRequest)
    {
        return (accessTokenRequest.hasDeviceAuthorizationNonce())
                ? handleDeviceAuthorization(accessTokenRequest) : handleAccessCode(accessTokenRequest);
    }

    /**
     * Authorize a token request that arrives with a device authorization nonce.
     * In this case, we don't have an access code record to look up, therefore we have to
     * get the principal information (it comes from SP via getDeviceAuthorization).
     */
    private AuthorizationRequest handleDeviceAuthorization(AccessTokenRequest accessTokenRequest)
    {
        AuthenticatedPrincipal principal;
        Client client = getClientForRequest(accessTokenRequest);
        Set<String> scopes = client.getScopes();

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

        String uri = accessTokenRequest.getRedirectUri();
        if (uri != null && (!uri.equalsIgnoreCase(authReq.getRedirectUri()))) {
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

    /**
     * Authorize a request that arrives with an OAuth access code. In this case, the principal
     * information comes from the original authorization request (stored in the db when
     * the access code was granted)
     */
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
        l.warn(msg);
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
