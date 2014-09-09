package com.aerofs.polaris;

import com.aerofs.baseline.mappers.BaseExceptionMapper;
import com.aerofs.polaris.api.InvalidTypeException;
import com.aerofs.polaris.resources.AlreadyExistsException;
import com.aerofs.polaris.resources.InvalidIDException;
import com.aerofs.polaris.resources.NameConflictException;
import com.aerofs.polaris.resources.NotFoundException;
import com.aerofs.polaris.resources.UpdateFailedException;
import com.aerofs.polaris.resources.VersionMismatchException;

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
        } else if (throwable instanceof InvalidIDException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof UpdateFailedException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof NameConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof AlreadyExistsException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof VersionMismatchException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof NotFoundException) {
            return Response.Status.NOT_FOUND;
        } else {
            return super.getResponseStatus(throwable);
        }
    }
}
