/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.syncstat;

import java.net.URL;

import com.aerofs.lib.C;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.servlet.AbstractServletClientHandler;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.SyncStatus.SyncStatusServiceStub.SyncStatusServiceStubCallbacks;

public class SyncStatusClientHandler
        extends AbstractServletClientHandler
        implements SyncStatusServiceStubCallbacks
{
    public SyncStatusClientHandler(URL url)
    {
        super(url, C.SS_POST_PARAM_PROTOCOL, C.SS_POST_PARAM_DATA, C.SS_PROTOCOL_VERSION);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
