/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.server.resources;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.inject.Singleton;
import javax.ws.rs.core.Response.Status;

@Singleton
public final class InvalidCSRExceptionMapper extends BaseExceptionMapper<InvalidCSRException>
{

    public InvalidCSRExceptionMapper()
    {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(InvalidCSRException throwable)
    {
        return 100;
    }

    @Override
    protected String getErrorName(InvalidCSRException throwable)
    {
        return throwable.getClass().getSimpleName();
    }

    @Override
    protected String getErrorText(InvalidCSRException throwable)
    {
        return throwable.getMessage();
    }

    @Override
    protected Status getHttpResponseStatus(InvalidCSRException throwable)
    {
        return Status.BAD_REQUEST;
    }
}
