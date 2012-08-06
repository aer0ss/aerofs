/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.IUnicastListener;
import com.aerofs.daemon.tng.StreamMap;
import com.aerofs.daemon.tng.base.pipeline.IConnection;
import com.aerofs.daemon.tng.base.pipeline.IPipeline;
import com.aerofs.daemon.tng.base.pipeline.IPipelineBuilder;
import com.aerofs.daemon.tng.base.pipeline.IPipelineEventSink;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.base.pipeline.IStateContainer;
import com.aerofs.daemon.tng.base.pulse.PulseInitiatorHandler;
import com.aerofs.daemon.tng.base.pulse.PulseResponderHandler;
import com.aerofs.daemon.tng.base.pulse.PulseState;
import com.aerofs.daemon.tng.base.pulse.ex.ExPulsingFailed;
import com.aerofs.daemon.tng.base.streams.StreamFactory;
import com.aerofs.daemon.tng.base.streams.StreamHandler;
import com.google.common.util.concurrent.ListenableFuture;

public class BasePipelineFactory implements IPipelineFactory
{
    private static final String PULSE_STATE_KEY = PulseState.class.getSimpleName();

    protected final ISingleThreadedPrioritizedExecutor _executor;
    private final IUnicastListener _unicastListener;

    public BasePipelineFactory(ISingleThreadedPrioritizedExecutor executor,
            IUnicastListener listener)
    {
        _executor = executor;
        _unicastListener = listener;
    }

    @Override
    public final IPipeline getPipeline_(IPeer peer, IConnection connection,
            IPipelineEventSink outgoingEventSink)
    {
        IPipelineBuilder builder = Pipeline.builder_(peer.getDID_(), peer.getPeerStateContainer_(),
                connection, outgoingEventSink);

        // Let the custom handlers be attached first, in case the user
        // uses addFirst_ and tries to insert their handler before WireHandler
        builder = attachCustomHandlers(builder);

        // Now we need to add low-level, specific handlers to the custom pipeline
        // for things to work out-of-the-box

        // Add the all important WireHandler that serializes and deserializes
        // bytes into PBTPHeaders and vice versa. This must be first, as no other
        // handler works in terms of bytes
        builder.addFirst_(new WireHandler());

        // Add the PulseHandlers after the custom handlers
        // Retrieve an existing PulseState if it exists, or create a new one
        // The only time a PulseState won't exist in the PeerStateContainer is
        // upon the first creation of a pipeline
        PulseState pulseState = makeOrGetPulseState(peer.getPeerStateContainer_());
        builder.addLast_(new PulseInitiatorHandler(_executor, pulseState));
        builder.addLast_(new PulseResponderHandler());

        // Add a UnicastPacket Handler
        builder.addLast_(new UnicastPacketHandler(_unicastListener));

        // Add the Stream Handler, which allows Streams to work.
        IStreamFactory streamFactory = new StreamFactory(_executor, peer.getDID_());
        builder.addLast_(new StreamHandler(_unicastListener, _executor, streamFactory,
                new StreamMap<IIncomingStream>(), new StreamMap<IOutgoingStream>()));

        return builder.build_();
    }

    /**
     * This method is meant to be overriden by subclasses. It offers the oppurtunity to add custom
     * handlers. Anything added to this builder will only need to work with IncomingAeroFSPacket
     * messages and OutgoingAeroFSPacket messages.
     *
     * @param builder A conveniently constructed builder for attaching handlers
     * @return The builder to use to build the pipeline. Usually the same as the builder that was
     *         passed in
     */
    protected IPipelineBuilder attachCustomHandlers(IPipelineBuilder builder)
    {
        return builder;
    }

    /**
     * Retrieves the PulseState from the given IStateContainer, or creates a new PulseState object
     * and stores it in the container if it did not originally exist
     *
     * @param container The container to check and insert into
     * @return A PulseState object, either retrieved from the container or newly created
     */
    private PulseState makeOrGetPulseState(IStateContainer container)
    {
        Object state = container.getState_(PULSE_STATE_KEY);
        if (state == null) {
            // The state is not set yet, so create the PulseState object
            final PulseState pulseState = new PulseState(DaemonParam.MAX_PULSE_FAILURES,
                    DaemonParam.INIT_PULSE_TIMEOUT, DaemonParam.MAX_PULSE_TIMEOUT);

            // Add the state to the container
            ListenableFuture<Object> future = container.addState_(PULSE_STATE_KEY, pulseState);

            // Have the PulseState object destroy itself when the Peer goes offline
            future.addListener(new Runnable()
            {
                @Override
                public void run()
                {
                    pulseState.destroy_(new ExPulsingFailed("peer disconnected"));
                }
            }, _executor);

            state = pulseState;
        }

        assert state instanceof PulseState;

        return (PulseState) state;
    }
}
