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

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.ws.rs.core.SecurityContext;

/**
 * Represents the result of an
 * {@link com.aerofs.baseline.auth.Authenticator#authenticate}
 * call. This is a tri-state return:
 * <ul>
 *     <li><pre>UNSUPPORTED</pre>: this request cannot be verified by the implementation</li>
 *     <li><pre>FAILED</pre>: this request can be verified by this implementation, but verification failed due to improper credentials</li>
 *     <li><pre>SUCCEEDED</pre>: this request can be verified by this implementation, and verification succeeded</li>
 * </ul>
 * Note that {@code FAILED} should <strong>NOT</strong> be returned if the
 * implementation fails (because of network error, parsing, etc.). If that occurs
 * the implementation <strong>MUST</strong> throw an
 * {@link com.aerofs.baseline.auth.AuthenticationException} instead.
 */
@Immutable
public final class AuthenticationResult {

    /**
     * Convenience instance of {@code AuthenticationResult} denoting that
     * the implementation <strong>cannot</strong> authenticate this request.
     */
    public static final AuthenticationResult UNSUPPORTED = new AuthenticationResult(Status.UNSUPPORTED, UnauthenticatedSecurityContext.UNAUTHENTICATED_SECURITY_CONTEXT);

    /**
     * Convenience instance of {@code AuthenticationResult}
     * denoting a failed authentication attempt.
     */
    public static final AuthenticationResult FAILED = new AuthenticationResult(Status.FAILED, UnauthenticatedSecurityContext.UNAUTHENTICATED_SECURITY_CONTEXT);

    /**
     * Result of an authentication operation.
     */
    public enum Status {

        /** The request cannot be verified by the authenticator. */
        UNSUPPORTED,

        /** The request can be verified by the authenticator, but verification failed. */
        FAILED,

        /** The request can be verified by the authenticator and verification succeeded. */
        SUCCEEDED,
    }

    private final Status status;

    @Nullable
    private final SecurityContext securityContext;

    /**
     * Constructor.
     *
     * @param status result of the authentication operation
     * @param securityContext {@code null} if the implementation cannot authenticate
     *                        the request, or authentication failed. A valid {@code SecurityContext}
     *                        instance otherwise
     */
    public AuthenticationResult(Status status, @Nullable SecurityContext securityContext) {
        this.status = status;
        this.securityContext = securityContext;
    }

    /**
     * @return result of the authentication operation
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return valid instance identifying the requester; {@code null} otherwise
     */
    @Nullable
    public SecurityContext getSecurityContext() {
        return securityContext;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthenticationResult other = (AuthenticationResult) o;
        return status == other.status && Objects.equal(securityContext, other.securityContext);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(status, securityContext);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("status", status)
                .add("securityContext", securityContext)
                .toString();
    }
}
