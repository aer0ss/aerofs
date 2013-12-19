package com.aerofs.daemon.rest.event;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.rest.api.Error;
import com.aerofs.lib.event.Prio;
import com.aerofs.rest.api.Error.Type;
import org.slf4j.Logger;

import javax.ws.rs.core.MediaType;
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
        ResponseBuilder bd;
        try {
            execute(Prio.LO);
            bd = response();
        } catch (RuntimeException e) {
            // runtime exceptions are rethrown to be handled by jersey ExceptionMapper(s)
            throw e;
        } catch (Exception e) {
            bd = handleException(e).type(MediaType.APPLICATION_JSON_TYPE);
        }
        return bd.build();
    }

    protected ResponseBuilder response()
    {
        return _result instanceof ResponseBuilder
                ? (ResponseBuilder)_result
                : Response.status(Status.OK).entity(_result);
    }

    protected ResponseBuilder handleException(Exception e)
    {
        if (e instanceof ExNotFound || e instanceof ExNotDir) {
            return Response
                    .status(Status.NOT_FOUND)
                    .entity(new Error(Type.NOT_FOUND, e.getMessage()));
        } else if (e instanceof ExNoPerm) {
            return Response
                    .status(Status.FORBIDDEN)
                    .entity(new Error(Type.FORBIDDEN, e.getMessage()));
        } else if (e instanceof ExBadArgs) {
            return Response
                    .status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS, e.getMessage()));
        } else {
            l.error("internal error", e);
            return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new Error(Type.INTERNAL_ERROR, "Internal error: " + e.getClass()));
        }
    }
}
