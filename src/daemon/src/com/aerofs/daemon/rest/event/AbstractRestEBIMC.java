package com.aerofs.daemon.rest.event;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.rest.api.Error;
import com.aerofs.lib.event.Prio;
import org.slf4j.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

public abstract class AbstractRestEBIMC extends AbstractEBIMC
{
    private static final Logger l = Loggers.getLogger(AbstractRestEBIMC.class);

    public final UserID _user;

    private Object _result;

    protected AbstractRestEBIMC(IIMCExecutor imce, UserID user)
    {
        super(imce);
        _user = user;
    }

    public void setResult_(Object result)
    {
        _result = result;
    }

    public Response execute()
    {
        try {
            execute(Prio.LO);
            return response().build();
        } catch (Exception e) {
            return handleException(e).build();
        }
    }

    protected ResponseBuilder response()
    {
        return _result instanceof ResponseBuilder
                ? (ResponseBuilder)_result
                : Response.status(Status.OK).entity(_result);
    }

    protected ResponseBuilder handleException(Exception e)
    {
        if (e instanceof ExNotFound){
            return Response.status(Status.NOT_FOUND)
                    .entity(new Error(Type.NOT_FOUND.name(), e.getMessage()));
        } else if (e instanceof ExNoPerm) {
            return Response.status(Status.FORBIDDEN)
                 .entity(new Error(Type.NO_PERM.name(), e.getMessage()));
        } else if (e instanceof ExBadArgs) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS.name(), e.getMessage()));
        } else if (e instanceof AbstractExWirable) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(((AbstractExWirable)e).getWireType().name(), e.getMessage()));
        } else {
            l.error("", e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
}
