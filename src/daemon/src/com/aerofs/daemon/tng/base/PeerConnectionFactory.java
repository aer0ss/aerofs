/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.pipeline.IPipeline;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;

class PeerConnectionFactory
{
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final IUnicastConnectionService _unicastConnectionService;
    private final IPipelineFactory _pipelineFactory;

    PeerConnectionFactory(ISingleThreadedPrioritizedExecutor executor,
            IUnicastConnectionService unicastConnectionService, IPipelineFactory pipelineFactory)
    {
        this._executor = executor;
        this._unicastConnectionService = unicastConnectionService;
        this._pipelineFactory = pipelineFactory;
    }

    private PeerConnection create_(IPeer peer, IUnicastConnection unicast)
    {
        UnicastConnectionSink unicastSink = new UnicastConnectionSink(unicast);
        PeerConnection connection = PeerConnection.getInstance_(_executor, unicast);

        IPipeline pipeline = _pipelineFactory.getPipeline_(peer, connection, unicastSink);

        connection.setPipeline_(pipeline);
        return connection;
    }

    PeerConnection createConnection_(IPeer peer)
    {
        final IUnicastConnection unicast = _unicastConnectionService.createConnection_(
                peer.getDID_());
        return create_(peer, unicast);
    }

    PeerConnection createConnection_(IPeer peer, IUnicastConnection unicast)
    {
        return create_(peer, unicast);
    }
}
