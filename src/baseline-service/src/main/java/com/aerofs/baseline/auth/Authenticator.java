/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.auth;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Implemented by classes that can authenticate
 * incoming HTTP requests using HTTP request headers.
 */
public interface Authenticator {

    /**
     * Human-readable name identifying this implementation.
     *
     * @return human-readable name for the authenticator implementation
     */
    String getName();

    /**
     * Authenticate an incoming HTTP request.
     * <p>
     * If this implementation cannot authenticate
     * the request an instance of {@code AuthenticationResult} with
     * {@code status} of either {@code UNSUPPORTED} or {@code FAILED} and
     * a {@code null} {@code SecurityContext} <strong>MUST</strong> be returned.
     * If authentication succeeds an instance of
     * {@code AuthenticationResult} with {@code status} {@code SUCCEEDED}
     * and a valid {@code SecurityContext} must be returned.
     * <p>
     * Note that {@code FAILED} should <strong>NOT</strong> be returned if the
     * implementation fails because of a network error, parsing failure, etc.
     * If that occurs the implementation <strong>MUST</strong> throw an
     * {@link com.aerofs.baseline.auth.AuthenticationException} instead.
     * <p>
     * @param headers HTTP request headers
     * @return an instance of {@code AuthenticationResult} indicating
     * the result of the authentication process
     * @throws com.aerofs.baseline.auth.AuthenticationException if the authentication process failed
     *
     * @see com.aerofs.baseline.auth.AuthenticationResult.Status
     * @see javax.ws.rs.core.SecurityContext
     */
    AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException;
}
