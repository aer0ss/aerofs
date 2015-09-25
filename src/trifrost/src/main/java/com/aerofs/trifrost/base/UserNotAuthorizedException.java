package com.aerofs.trifrost.base;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.ws.rs.core.Response;

/**
 * Thrown when a customer identified by {@code customerId} does not exist.
 */
public final class UserNotAuthorizedException extends Exception {
    private static final long serialVersionUID = 1852041539564018220L;

    public UserNotAuthorizedException() { super("Invalid or expired device authorization code."); }

    /**
     * Map instances of {@link com.aerofs.trifrost.base.UserNotAuthorizedException}
     * to specific error responses.
     */
    public static final class Mapper extends BaseExceptionMapper<UserNotAuthorizedException> {

        public Mapper() {
            super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
        }

        @Override
        protected int getErrorCode(UserNotAuthorizedException throwable) { return 403; }

        @Override
        protected String getErrorName(UserNotAuthorizedException throwable) { return throwable.getClass().getSimpleName(); }

        @Override
        protected String getErrorText(UserNotAuthorizedException throwable) { return throwable.getMessage(); }

        @Override
        protected Response.Status getHttpResponseStatus(UserNotAuthorizedException throwable) { return Response.Status.FORBIDDEN; }
    }
}
