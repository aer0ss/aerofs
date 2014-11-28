/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.inject.Singleton;
import javax.ws.rs.core.Response.Status;

@Singleton
public final class BlacklistedExceptionMapper extends BaseExceptionMapper<BlacklistedException>  {

    public BlacklistedExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(BlacklistedException throwable) {
        return 1984;
    }

    @Override
    protected String getErrorName(BlacklistedException throwable) {
        return "ACCESS_FORBIDDEN";
    }

    @Override
    protected String getErrorText(BlacklistedException throwable) {
        return throwable.getMessage();
    }

    @Override
    protected Status getHttpResponseStatus(BlacklistedException throwable) {
        return Status.FORBIDDEN;
    }
}
