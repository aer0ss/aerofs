package com.aerofs.sp.client;

import java.net.URL;

import com.aerofs.lib.C;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.base.net.AbstractHttpRpcClient;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;

public class SPClientHandler
        extends AbstractHttpRpcClient
        implements SPServiceStubCallbacks
{
    public SPClientHandler(URL url)
    {
        super(url, C.SP_POST_PARAM_PROTOCOL, C.SP_POST_PARAM_DATA, C.SP_PROTOCOL_VERSION);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
