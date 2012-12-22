/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.daemon.tng.IPeerDiagnoser;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.base.AbstractTransport;
import com.aerofs.daemon.tng.base.IEventLoop;
import com.aerofs.daemon.tng.base.IMaxcastService;
import com.aerofs.daemon.tng.base.IPresenceService;
import com.aerofs.daemon.tng.base.IUnicastService;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.base.id.DID;

import java.net.Proxy;

final class Jingle extends AbstractTransport
{
    Jingle(String id, Preference pref, IEventLoop eventLoop, IPresenceService presenceService,
            IUnicastService unicastService, IMaxcastService maxcastService)
    {
        super(id, pref, eventLoop, presenceService, unicastService, maxcastService);
    }

    //--------------------------------------------------------------------------
    //
    //
    // factory methods
    //
    //
    //--------------------------------------------------------------------------

    public static Jingle getInstance_(IEventLoop eventLoop, String id, Preference pref,
            DID localdid, Proxy proxy, IPipelineFactory pipelineFactory,
            ITransportListener listener, IPeerDiagnoser peerDiagnoser,
            ILinkStateService networkLinkStateService, IPresenceService presenceService,
            IMaxcastService maxcastService)
    {
        return null; // FIXME: wait until Adam is done
    }
}
