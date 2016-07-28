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

import javax.annotation.concurrent.Immutable;

/**
 * Thrown by an {@link com.aerofs.baseline.auth.Authenticator}
 * implementation to signal that the authenticator failed
 * <strong>during</strong> authentication.
 */
@SuppressWarnings("unused")
@Immutable
public final class AuthenticationException extends RuntimeException {

    private static final long serialVersionUID = -3984422008592302011L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
