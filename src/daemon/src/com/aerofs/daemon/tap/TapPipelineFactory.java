/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tap.filter.IMessageFilterListener;
import com.aerofs.daemon.tap.filter.MessageFilter;
import com.aerofs.daemon.tng.IUnicastListener;
import com.aerofs.daemon.tng.base.BasePipelineFactory;
import com.aerofs.daemon.tng.base.pipeline.IPipelineBuilder;

public class TapPipelineFactory extends BasePipelineFactory
{
    private final IMessageFilterListener _messageFilterListener;

    public TapPipelineFactory(ISingleThreadedPrioritizedExecutor executor,
            IUnicastListener listener, IMessageFilterListener messageFilterListener)
    {
        super(executor, listener);
        this._messageFilterListener = messageFilterListener;
    }

    @Override
    protected IPipelineBuilder attachCustomHandlers(IPipelineBuilder builder)
    {
        return builder.addLast_(new MessageFilter(_executor, _messageFilterListener, _executor));
    }
}
