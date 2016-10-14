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

/**
 * Representation of an Access Token response. See <a
 * href="https://tools.ietf.org/html/draft-ietf-oauth-v2-30#section-4.1.3">this
 * section</a> of the spec for more info.
 * 
 */
public class AccessTokenResponse
{
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String refreshToken;
    private String scope;

    public AccessTokenResponse(String accessToken, String tokenType, long expiresIn,
            String refreshToken, String scope) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.scope = scope;
    }

    @Override
    public String toString() {
        return "AccessTokenResponse [accessToken=" + accessToken
              + ", tokenType=" + tokenType
              + ", expiresInSeconds=" + expiresIn
              + ", refreshToken=" + refreshToken
              + ", scope=" + scope + "]";
    }
}
