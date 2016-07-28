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

package com.aerofs.baseline.admin;

import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.baseline.errors.BaselineError;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

/**
 * Specialization of {@code BaseExceptionMapper} that converts
 * {@code InvalidCommandException} instances to their {@link com.aerofs.baseline.errors.ServiceError}
 * representation.
 * <br>
 * This mapper:
 * <ul>
 *     <li>Does not log a stack trace.</li>
 *     <li>Returns an HTTP response with status code {@code 404 (NOT FOUND)}.</li>
 *     <li>Does not return the exception stack in the response.</li>
 * </ul>
 */
@ThreadSafe
@Singleton
public final class InvalidCommandExceptionMapper extends BaseExceptionMapper<InvalidCommandException> {

    public InvalidCommandExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(InvalidCommandException throwable) {
        return BaselineError.INVALID_ADMIN_COMMAND.code();
    }

    @Override
    protected String getErrorName(InvalidCommandException throwable) {
        return BaselineError.INVALID_ADMIN_COMMAND.name();
    }

    @Override
    protected String getErrorText(InvalidCommandException throwable) {
        return throwable.getMessage();
    }

    @Override
    protected Response.Status getHttpResponseStatus(InvalidCommandException throwable) {
        return Response.Status.NOT_FOUND;
    }
}
