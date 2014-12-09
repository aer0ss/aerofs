package com.aerofs.baseline.simple.resources;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.inject.Singleton;
import javax.ws.rs.core.Response;

/**
 * Map instances of {@link InvalidCustomerException}
 * to specific error responses.
 */
@Singleton
public final class InvalidCustomerExceptionMapper extends BaseExceptionMapper<InvalidCustomerException> {

    public InvalidCustomerExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(InvalidCustomerException throwable) {
        return 111;
    }

    @Override
    protected String getErrorName(InvalidCustomerException throwable) {
        return throwable.getClass().getSimpleName();
    }

    @Override
    protected String getErrorText(InvalidCustomerException throwable) {
        return throwable.getMessage();
    }

    @Override
    protected Response.Status getHttpResponseStatus(InvalidCustomerException throwable) {
        return Response.Status.NOT_FOUND;
    }
}
