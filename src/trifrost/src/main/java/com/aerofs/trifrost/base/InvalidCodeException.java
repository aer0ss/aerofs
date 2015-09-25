package com.aerofs.trifrost.base;

import com.aerofs.baseline.errors.BaseExceptionMapper;

import javax.ws.rs.core.Response;

/**
 * Thrown when a customer identified by {@code customerId} does not exist.
 */
public final class InvalidCodeException extends Exception {
    private static final long serialVersionUID = 8657794515166556936L;

    public InvalidCodeException() { super("Invalid or expired device authorization code."); }

    /**
     * Map instances of {@link InvalidCodeException}
     * to specific error responses.
     */
    public static final class Mapper extends BaseExceptionMapper<InvalidCodeException> {

        public Mapper() {
            super(ErrorResponseEntity.NO_STACK_IN_RESPONSE, StackLogging.DISABLE_LOGGING);
        }

        @Override
        protected int getErrorCode(InvalidCodeException throwable) { return 403; }

        @Override
        protected String getErrorName(InvalidCodeException throwable) { return throwable.getClass().getSimpleName(); }

        @Override
        protected String getErrorText(InvalidCodeException throwable) { return throwable.getMessage(); }

        @Override
        protected Response.Status getHttpResponseStatus(InvalidCodeException throwable) { return Response.Status.FORBIDDEN; }
    }
}
