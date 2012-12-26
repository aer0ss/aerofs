/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ritual;

import com.aerofs.base.net.AbstractRpcClientHandler;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Ritual.RitualServiceStub.RitualServiceStubCallbacks;

public class RitualClientHandler extends AbstractRpcClientHandler implements RitualServiceStubCallbacks
{
    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
