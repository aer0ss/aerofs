package com.aerofs.daemon.rest.event;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.RestObject;
import com.google.common.collect.Range;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

public class EIFileContent extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final String _etag;
    public final String _rangeset;

    public EIFileContent(IIMCExecutor imce, UserID userid, RestObject object,
            @Nullable String etag, @Nullable String rangeset)
    {
        super(imce, userid);
        _object = object;
        _etag = etag;
        _rangeset = rangeset;
    }

    @Override
    public ResponseBuilder response()
    {
        return super.response()
                .header("Accept-Ranges", "bytes");
    }
}
