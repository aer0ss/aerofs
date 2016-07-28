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

import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.baseline.errors.BaselineError;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

@ThreadSafe
@Singleton
public final class AuthenticationExceptionMapper extends BaseExceptionMapper<AuthenticationException> {

    public AuthenticationExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(AuthenticationException throwable) {
        return BaselineError.UNPARSEABLE_INPUT.code();
    }

    @Override
    protected String getErrorName(AuthenticationException throwable) {
        return BaselineError.UNPARSEABLE_INPUT.name();
    }

    @Override
    protected String getErrorText(AuthenticationException throwable) {
        return throwable.getMessage();
    }

    protected Response.Status getHttpResponseStatus(AuthenticationException throwable) {
        return Response.Status.BAD_REQUEST;
    }
}
