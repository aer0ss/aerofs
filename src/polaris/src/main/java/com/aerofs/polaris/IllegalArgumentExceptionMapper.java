package com.aerofs.polaris;

import com.aerofs.baseline.mappers.BaseExceptionMapper;
import com.aerofs.polaris.api.InvalidTypeException;
import com.aerofs.polaris.resources.InvalidObjectIDException;
import com.aerofs.polaris.resources.ObjectNotFoundException;
import com.aerofs.polaris.resources.ObjectUpdateFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public final class IllegalArgumentExceptionMapper extends BaseExceptionMapper<PolarisException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IllegalArgumentExceptionMapper.class);

    public IllegalArgumentExceptionMapper() {
        super(false);
    }

    protected Response.Status getResponseStatus(Throwable throwable) {
        if (throwable instanceof InvalidTypeException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof InvalidObjectIDException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof ObjectUpdateFailedException) {
            return Response.Status.BAD_REQUEST;
        } else if (throwable instanceof ObjectNotFoundException) {
            return Response.Status.NOT_FOUND;
        } else {
            return super.getResponseStatus(throwable);
        }
    }

    @Override
    protected void logError(int errorNumber, Throwable cause) {
        LOGGER.warn("request failed error:{}", errorNumber);
    }
}
