package com.aerofs.daemon.rest.event;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.rest.api.Error;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.event.Prio;
import org.slf4j.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
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

    protected static Path mkpath(String path)
    {
        // TODO: make SID part of API for multiroot support?
        // or add pseudo-level to distinguish between ext roots?
        // TODO: what about TS
        return Path.fromString(Cfg.rootSID(), path);
    }

    public void setResult_(Object result)
    {
        _result = result;
    }

    public Response execute()
    {
        try {
            execute(Prio.LO);
            return Response.status(Status.OK).entity(_result).build();
        } catch (ExNotFound e) {
            return Response.status(Status.NOT_FOUND)
                    .entity(new Error(e.getWireType().name(), e.getMessage())).build();
        } catch (ExNoPerm e) {
            return Response.status(Status.FORBIDDEN)
                    .entity(new Error(e.getWireType().name(), e.getMessage())).build();
        } catch (ExBadArgs e) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(e.getWireType().name(), e.getMessage())).build();
        } catch (AbstractExWirable e) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(e.getWireType().name(), e.getMessage())).build();
        } catch (Exception e) {
            l.error("", e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
}
