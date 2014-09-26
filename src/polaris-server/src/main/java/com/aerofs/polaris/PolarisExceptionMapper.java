package com.aerofs.polaris;

import com.aerofs.baseline.BaseExceptionMapper;
import com.aerofs.polaris.logical.NameConflictException;
import com.aerofs.polaris.logical.NotFoundException;
import com.aerofs.polaris.logical.VersionConflictException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public final class PolarisExceptionMapper extends BaseExceptionMapper<PolarisException> {

    public PolarisExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    protected Response.Status getResponseStatus(PolarisException throwable) {
        if (throwable instanceof NameConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof VersionConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof NotFoundException) {
            return Response.Status.NOT_FOUND;
        } else {
            return super.getResponseStatus(throwable);
        }
    }
}
