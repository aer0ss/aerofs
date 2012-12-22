/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;

class PeerFactory
{
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final IUnicastConnectionService _unicastConnectionService;
    private final IPipelineFactory _pipelineFactory;

    PeerFactory(ISingleThreadedPrioritizedExecutor executor,
            IUnicastConnectionService unicastConnectionService, IPipelineFactory pipelineFactory)
    {
        this._executor = executor;
        this._unicastConnectionService = unicastConnectionService;
        this._pipelineFactory = pipelineFactory;
    }

    Peer getInstance_(DID did)
    {
        PeerConnectionFactory connectionFactory = new PeerConnectionFactory(_executor,
                _unicastConnectionService, _pipelineFactory);

        return Peer.getInstance_(did, _executor, connectionFactory);
    }
}