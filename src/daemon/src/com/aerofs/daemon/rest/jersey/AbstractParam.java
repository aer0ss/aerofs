package com.aerofs.daemon.rest.jersey;

import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.rest.api.Error;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Base class for Jersey params
 */
public abstract class AbstractParam<T>
{
    private final T value;

    @SuppressWarnings({"AbstractMethodCallInConstructor", "OverriddenMethodCallDuringObjectConstruction"})
    protected AbstractParam(String input)
    {
        try {
            this.value = parse(input);
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS.name(),
                            String.format("Invalid parameter: %s (%s)", input, e.getMessage())))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }
    }

    protected abstract T parse(String input) throws Exception;

    public T get()
    {
        return value;
    }
}
