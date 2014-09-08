package com.aerofs.polaris;

import com.aerofs.baseline.mappers.BaseExceptionMapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public final class IllegalArgumentExceptionMapper extends BaseExceptionMapper<IllegalArgumentException> {

    public IllegalArgumentExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    protected Response.Status getResponseStatus(Throwable throwable) {
        return Response.Status.BAD_REQUEST;
    }
}
