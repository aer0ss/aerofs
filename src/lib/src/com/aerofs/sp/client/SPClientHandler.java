package com.aerofs.sp.client;

import com.aerofs.base.net.AbstractHttpRpcClient;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.lib.C;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;

import java.net.URL;

public class SPClientHandler extends AbstractHttpRpcClient implements SPServiceStubCallbacks
{
    public SPClientHandler(URL url, IURLConnectionConfigurator conf)
    {
        super(url, C.SP_POST_PARAM_PROTOCOL, C.SP_POST_PARAM_DATA, C.SP_PROTOCOL_VERSION, conf);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
