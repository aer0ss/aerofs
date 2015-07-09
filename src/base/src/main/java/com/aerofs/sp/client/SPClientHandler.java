package com.aerofs.sp.client;

import com.aerofs.base.net.AbstractHttpRpcClient;
import com.aerofs.base.net.IURLConnectionConfigurator;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;

import java.net.URL;

import static com.aerofs.sp.client.SPProtocol.*;

public class SPClientHandler extends AbstractHttpRpcClient implements SPServiceStubCallbacks
{
    public SPClientHandler(URL url, IURLConnectionConfigurator conf)
    {
        super(url, SP_POST_PARAM_PROTOCOL, SP_POST_PARAM_DATA, SP_PROTOCOL_VERSION, conf);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
