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

import com.aerofs.bifrost.oaaas.auth.principal.UserPassCredentials;
import com.aerofs.bifrost.oaaas.model.AccessToken;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.oaaas.repository.AccessTokenRepository;
import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import com.aerofs.bifrost.server.Transactional;
import com.aerofs.oauth.VerifyTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;

import static com.aerofs.bifrost.oaaas.resource.TokenResource.BASIC_REALM;
import static com.aerofs.bifrost.oaaas.resource.TokenResource.WWW_AUTHENTICATE;

/**
 * Resource for handling the call from resource servers to validate an access
 * token. As this is not part of the oauth2 <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2">spec</a>, we have taken
 * the Google <a href=
 * "https://developers.google.com/accounts/docs/OAuth2Login#validatingtoken"
 * >specification</a> as basis.
 */
@Path("/tokeninfo")
@Transactional(readOnly = true)
@Produces(MediaType.APPLICATION_JSON)
public class VerifyResource {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyResource.class);

    @Inject
    private AccessTokenRepository accessTokenRepository;

    @Inject
    private ResourceServerRepository resourceServerRepository;

    // FIXME: we should probably call over to SP to get updated value for Organization ID...
    // but maybe not every time. Do we need a "detailed=true" param?
    @GET
    public Response verifyToken(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @QueryParam("access_token") String accessToken)
            throws IOException
    {
        UserPassCredentials credentials = new UserPassCredentials(authorization);
        LOG.debug("verify-token token: {}, credentials: {}", accessToken, credentials);

        ResourceServer rs = resourceServerRepository.findByKey(credentials.getUsername());
        if (rs == null || !rs.getSecret().equals(credentials.getPassword())) {
            LOG.warn("no resource server for credentials {}", credentials);
            return Response
                    .status(Status.UNAUTHORIZED)
                    .header(WWW_AUTHENTICATE, BASIC_REALM)
                    .build();
        }

        AccessToken token = accessTokenRepository.findByToken(accessToken);
        if (token == null || !rs.containsClient(token.getClient())) {
            LOG.warn("Access token {} not found for resource server '{}'", accessToken, rs.getName());
            return Response
                    .status(Status.NOT_FOUND)
                    .entity(VerifyTokenResponse.NOT_FOUND)
                    .build();
        }

        if (tokenExpired(token)) {
            LOG.warn("Token {} is expired.", accessToken);
            return Response
                    .status(Status.GONE)
                    .entity(VerifyTokenResponse.EXPIRED)
                    .build();
        }

        LOG.debug("access token {} valid for user {}", accessToken, credentials);
        return Response
                .ok(new VerifyTokenResponse(
                        token.getClient().getClientId(),
                        token.getScopes(),
                        token.getExpires(),
                        token.getPrincipal(),
                        token.getMdid()))
                .build();
    }

    private boolean tokenExpired(AccessToken token) {
        return token.getExpires() != 0 && token.getExpires() < System.currentTimeMillis();
    }
}
