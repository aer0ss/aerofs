package com.aerofs.polaris;

import com.aerofs.baseline.errors.BaseExceptionMapper;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.logical.*;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public final class PolarisExceptionMapper extends BaseExceptionMapper<PolarisException> {

    public PolarisExceptionMapper() {
        super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
    }

    @Override
    protected int getErrorCode(PolarisException throwable) {
        return throwable.getErrorCode().code();
    }

    @Override
    protected String getErrorName(PolarisException throwable) {
        return throwable.getErrorCode().name();
    }

    @Override
    protected String getErrorText(PolarisException throwable) {
        return throwable.getSimpleMessage();
    }

    @Override
    protected void addErrorFields(PolarisException throwable, Map<String, Object> errorFields) {
        throwable.addErrorFields(errorFields);
    }

    protected Response.Status getHttpResponseStatus(PolarisException throwable) {
        if (throwable instanceof AccessException) {
            return Response.Status.FORBIDDEN;
        } else if (throwable instanceof NameConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof VersionConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof ParentConflictException) {
            return Response.Status.CONFLICT;
        } else if (throwable instanceof NotFoundException) {
            return Response.Status.NOT_FOUND;
        } else if (throwable instanceof ObjectLockedException) {
            return Response.Status.CONFLICT;
        } else {
            return Response.Status.INTERNAL_SERVER_ERROR;
        }
    }
}
