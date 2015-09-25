package com.aerofs.trifrost.base;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.ws.rs.core.Response;

public final class DeviceNotFoundException extends Exception {
    private static final long serialVersionUID = 7166806730353197957L;

    public DeviceNotFoundException() { super("Invalid or expired device authorization code."); }

    /**
     * Map instances of {@link com.aerofs.trifrost.base.DeviceNotFoundException}
     * to specific error responses.
     */
    public static final class Mapper extends BaseExceptionMapper<DeviceNotFoundException> {

        public Mapper() {
            super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
        }

        @Override
        protected int getErrorCode(DeviceNotFoundException throwable) { return 404; }

        @Override
        protected String getErrorName(DeviceNotFoundException throwable) { return throwable.getClass().getSimpleName(); }

        @Override
        protected String getErrorText(DeviceNotFoundException throwable) { return throwable.getMessage(); }

        @Override
        protected Response.Status getHttpResponseStatus(DeviceNotFoundException throwable) { return Response.Status.NOT_FOUND; }
    }
}
