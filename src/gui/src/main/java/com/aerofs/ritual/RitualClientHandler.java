/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ritual;

import com.aerofs.base.C;
import com.aerofs.base.net.AbstractRpcClientHandler;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Ritual.RitualServiceStub.RitualServiceStubCallbacks;

public class RitualClientHandler extends AbstractRpcClientHandler implements RitualServiceStubCallbacks
{
    public RitualClientHandler()
    {
        // Construct a AbstractRpcClientHandler with no RPC timeout
        // TODO (GS): I added a timeout for the needs of MobileRpcClientHandler. Set this to
        //            non-zero if you want have a request timeout in Ritual too.
        super(0 * C.SEC);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
