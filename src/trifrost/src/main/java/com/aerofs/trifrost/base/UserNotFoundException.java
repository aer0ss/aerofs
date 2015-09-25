package com.aerofs.trifrost.base;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.ws.rs.core.Response;

/**
 * Thrown when a customer identified by {@code customerId} does not exist.
 */
public final class UserNotFoundException extends Exception {
    private static final long serialVersionUID = 7893349380792466352L;

    public UserNotFoundException() { super("Invalid or expired device authorization code."); }

    /**
     * Map instances of {@link UserNotFoundException}
     * to specific error responses.
     */
    public static final class Mapper extends BaseExceptionMapper<UserNotFoundException> {

        public Mapper() {
            super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
        }

        @Override
        protected int getErrorCode(UserNotFoundException throwable) { return 404; }

        @Override
        protected String getErrorName(UserNotFoundException throwable) { return throwable.getClass().getSimpleName(); }

        @Override
        protected String getErrorText(UserNotFoundException throwable) { return throwable.getMessage(); }

        @Override
        protected Response.Status getHttpResponseStatus(UserNotFoundException throwable) { return Response.Status.NOT_FOUND; }
    }
}
