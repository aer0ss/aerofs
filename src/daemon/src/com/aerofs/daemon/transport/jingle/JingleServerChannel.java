/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.jingle.SignalThread.IIncomingTunnelListener;
import com.aerofs.j.Jid;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.TunnelSessionClient;
import org.jboss.netty.channel.AbstractServerChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.DefaultServerChannelConfig;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.netty.channel.Channels.fireChannelInterestChangedLater;
import static org.jboss.netty.channel.Channels.fireChannelOpen;
import static org.jboss.netty.channel.Channels.future;
import static org.jboss.netty.channel.Channels.succeededFuture;

class JingleServerChannel extends AbstractServerChannel implements IIncomingTunnelListener
{
    private static final Logger l = Loggers.getLogger(AbstractServerChannel.class);

    private final AtomicInteger interestOps = new AtomicInteger(Channel.OP_READ_WRITE);
    private final ChannelConfig channelConfig = new DefaultServerChannelConfig();
    private final SignalThread signalThread;
    private final JingleChannelWorker channelWorker;
    private final JingleChannelWorker acceptedChannelWorker;

    private volatile JingleAddress localAddress;

    /**
     * Constructor.
     *
     * @param signalThread libjingle signal thread singleton
     * @param channelWorker {@code JingleChannelWorker} that will handle all incoming events for the server channel
     * @param acceptedChannelWorker {@code JingleChannelWorker} that will handle all incoming events for accepted jingle channels
     * @param factory the factory which created this channel
     * @param pipeline the pipeline which is going to be attached to this channel
     * @param sink the sink which will receive downstream events from the pipeline and send upstream
     */
    protected JingleServerChannel(
            SignalThread signalThread,
            JingleChannelWorker channelWorker,
            JingleChannelWorker acceptedChannelWorker,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink)
    {
        super(factory, pipeline, sink);

        this.signalThread = signalThread;
        this.channelWorker = channelWorker;
        this.acceptedChannelWorker = acceptedChannelWorker;

        signalThread.setIncomingTunnelListener(this);
        fireChannelOpen(this);
    }

    @Override
    public ChannelConfig getConfig()
    {
        return channelConfig;
    }

    @Override
    public boolean isBound()
    {
        return localAddress != null;
    }

    @Override
    public boolean setClosed()
    {
        return super.setClosed();
    }

    @Override
    public ChannelFuture setInterestOps(int interestOps)
    {
        this.interestOps.set(interestOps);
        return succeededFuture(this);
    }

    @Override
    public void setInterestOpsNow(int interestOps)
    {
        this.interestOps.set(interestOps);
    }

    @Override
    public int getInterestOps()
    {
        return this.interestOps.get();
    }

    @Override
    public boolean isReadable()
    {
        return (getInterestOps() & Channel.OP_READ) != 0;
    }

    @Override
    public boolean isWritable()
    {
        return (getInterestOps() & Channel.OP_READ) == 0;
    }

    @Override
    public ChannelFuture setReadable(boolean readable)
    {
        int newInterestOps = (readable ? getInterestOps() | OP_READ : getInterestOps() & ~OP_READ);
        this.interestOps.set(newInterestOps);
        fireChannelInterestChangedLater(this);
        return succeededFuture(this);
    }

    void setLocalAddress(JingleAddress address)
    {
        localAddress = address;
    }

    @Override
    public SocketAddress getLocalAddress()
    {
        return localAddress;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code null}, since server channels have no remote address
     */
    @Override
    public SocketAddress getRemoteAddress()
    {
        return null;
    }

    void execute(final ChannelFuture future, final Runnable task)
    {
        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    task.run();
                    future.setSuccess();
                } catch (Exception e) {
                    future.setFailure(e);
                }
            }
        });
    }

    @Override
    public void onIncomingTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session)
    {
        signalThread.assertSignalThread();

        if (!isBound() || !isOpen() || !isReadable()) {
            declineTunnel(client, jid, session, "server channel unavailable");
            return;
        }

        l.debug("{} incoming tunnel", JingleUtils.jid2didNoThrow(jid));

        JingleClientChannel channel = null;
        try {
            DID did = JingleUtils.jid2did(jid);
            JingleAddress address = new JingleAddress(did, jid);
            StreamInterface streamInterface = client.AcceptTunnel(session);

            channel = new JingleClientChannel(signalThread, acceptedChannelWorker, this, getFactory(), getConfig().getPipelineFactory().getPipeline(), getPipeline().getSink());
            channel.wrapStreamInterface(address, true, streamInterface);

            channel.setBound(future(channel), localAddress);
        } catch (Throwable t) {
            declineTunnel(client, jid, session, "error:" + t.getMessage());
            if (channel != null) {
                channel.onClose(new ExDeviceUnavailable("failed to accept incoming jingle tunnel", t));
            }
        }
    }

    private void declineTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session, String cause)
    {
        l.warn("{} decline tunnel err:{}", JingleUtils.jid2didNoThrow(jid), cause);
        client.DeclineTunnel(session);
    }
}
