package com.aerofs.polaris;

import com.aerofs.baseline.mappers.BaseExceptionMapper;
import com.aerofs.polaris.api.InvalidTypeException;
import com.aerofs.polaris.resources.InvalidObjectIDException;
import com.aerofs.polaris.resources.NameConflictException;
import com.aerofs.polaris.resources.ObjectAlreadyExistsException;
import com.aerofs.polaris.resources.ObjectNotFoundException;
import com.aerofs.polaris.resources.ObjectUpdateFailedException;
import com.aerofs.polaris.resources.ObjectVersionMismatchException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public final class PolarisExceptionMapper extends BaseExceptionMapper<PolarisException> {

    public PolarisExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    protected Response.Status getResponseStatus(Throwable throwable) {
        if (throwable instanceof InvalidTypeException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof InvalidObjectIDException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof ObjectUpdateFailedException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof NameConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof ObjectAlreadyExistsException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof ObjectVersionMismatchException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof ObjectNotFoundException) {
            return Response.Status.NOT_FOUND;
        } else {
            return super.getResponseStatus(throwable);
        }
    }
}
