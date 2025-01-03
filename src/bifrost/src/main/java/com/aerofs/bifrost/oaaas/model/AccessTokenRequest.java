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
package com.aerofs.bifrost.oaaas.model;

import com.aerofs.bifrost.oaaas.auth.OAuth2Validator.ValidationResponse;
import com.aerofs.bifrost.oaaas.auth.ValidationResponseException;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

/**
 * Representation of the AccessToken request defined in the <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2#page-27">spec</a>
 */
public class AccessTokenRequest {
    private String grantType;
    private String code;
    private String redirectUri;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String scope;
    // requested token duration in seconds (0 means indefinite)
    private Long expiresInSeconds;
    private CodeType codeType;
    private transient Client client;

    /**
     * Bifrost supports traditional OAuth access codes generated by the authentication path in
     * this component, or the device-authorization nonce generated by SP for a new device.
     */
    enum CodeType {
        /**
         * An OAuth-standard access code, generated by the
         * authentication path in Bifrost.
         */
        ACCESS_CODE,
        /**
         * A device-authorization nonce, generated by SP for a new-device request.
         */
        DEVICE_AUTH_NONCE
    }

    public static AccessTokenRequest fromMultiValuedFormParameters(MultivaluedMap<String, String> formParameters) {
        AccessTokenRequest atr = new AccessTokenRequest();
        atr.setClientId(nullSafeGetFormParameter("client_id", formParameters));
        atr.setClientSecret(nullSafeGetFormParameter("client_secret", formParameters));
        atr.setCode(nullSafeGetFormParameter("code", formParameters));
        atr.setGrantType(nullSafeGetFormParameter("grant_type", formParameters));
        atr.setRedirectUri(nullSafeGetFormParameter("redirect_uri", formParameters));
        atr.setRefreshToken(nullSafeGetFormParameter("refresh_token", formParameters));
        atr.setScope(nullSafeGetFormParameter("scope", formParameters));
        atr.setExpiresInSeconds(nullSafeGetFormParameter("expires_in", formParameters));
        String codeType = nullSafeGetFormParameter("code_type", formParameters);
        atr.codeType = codeType != null && codeType.equalsIgnoreCase("device_authorization") ?
                CodeType.DEVICE_AUTH_NONCE : CodeType.ACCESS_CODE;

        return atr;
    }

    private static String nullSafeGetFormParameter(String parameterName, MultivaluedMap<String, String> formParameters) {
        List<String> params = formParameters.get(parameterName);
        return params == null || params.isEmpty() ? null : params.get(0);
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getCode() {
        return code;
    }

    /**
     * If true, this access token request has a device authorization nonce value instead
     * of an access token.
     */
    public boolean hasDeviceAuthorizationNonce() {
        return codeType.equals(CodeType.DEVICE_AUTH_NONCE);
    }

    public String getDeviceAuthorizationNonce() {
        return hasDeviceAuthorizationNonce() ? code : null;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setExpiresInSeconds(String expiresInSeconds) {
        try {
            this.expiresInSeconds = (expiresInSeconds != null) ? Long.valueOf(expiresInSeconds) : null;
        } catch (NumberFormatException ignored) {
            throw new ValidationResponseException(ValidationResponse.INVALID_EXPIRES_IN);
        }
    }

    public Long getExpiresInSeconds() {
        return this.expiresInSeconds;
    }
}
