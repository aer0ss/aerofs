/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.base.BaseParam.Zephyr;
import com.aerofs.base.Loggers;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.net.ZephyrPipeHandler;
import com.aerofs.daemon.mobile.TransportDataExtension.TransportDataIQ;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.StringUtils;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.net.InetSocketAddress;

public class MobileServerZephyrConnector
{
    private static final Logger l = Loggers.getLogger(MobileServerZephyrConnector.class);
    private static final String MOBILE_SUBJECT = "mobile";

    static {
        TransportDataExtension.init();
    }

    private final ClientBootstrap _bootstrap;

    @Inject
    public MobileServerZephyrConnector(MobileServiceFactory mobileServiceFactory, ClientSocketChannelFactory clientChannelFactory)
    {
        _bootstrap = newClientBootstrap(mobileServiceFactory, clientChannelFactory);
    }

    private static ClientBootstrap newClientBootstrap(final MobileServiceFactory mobileServiceFactory, ClientSocketChannelFactory clientChannelFactory)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(clientChannelFactory);

        bootstrap.setOption("keepAlive", true);
        bootstrap.setOption("tcpNoDelay", true);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline()
                    throws Exception
            {
                ChannelPipeline p = Channels.pipeline();
                p.addLast("resolver", new AddressResolverHandler(null));
                p.addLast("zephyrPipeHandler", new ZephyrPipeHandler());
                mobileServiceFactory.appendToPipeline(p);
                return p;
            }
        });

        return bootstrap;
    }

    private InetSocketAddress getZephyrAddress()
    {
        return Zephyr.ADDRESS.get();
    }

    // called from XMPP
    public void setConnection(final Connection connection)
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                if (packet instanceof Message) {
                    processMessage(connection, (Message)packet);
                }
            }
        }, new AndFilter(new PacketTypeFilter(Message.class),
                new MessageTypeFilter(Message.Type.normal)));

        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                if (packet instanceof TransportDataIQ) {
                    processIQ(connection, (TransportDataIQ)packet);
                }

            }
        }, new AndFilter(new PacketTypeFilter(TransportDataIQ.class),
                new IQTypeFilter(IQ.Type.SET)));
    }

    private void processMessage(final Connection conn, final Message message)
    {
        if (!MOBILE_SUBJECT.equals(message.getSubject())) return;
        String fromName = StringUtils.parseName(message.getFrom());
        if (fromName == null) return;

        TransportDataExtension data = TransportDataExtension.getExtension(message);
        if (data == null) return;
        PBTPHeader proto = data.getProto();

        int remoteZid = proto.getZephyrInfo().getSourceZephyrId();
        l.debug("got remote zid: " + remoteZid);

        ChannelFuture future = _bootstrap.connect(getZephyrAddress());
        Channel channel = future.getChannel();
        final ZephyrPipeHandler zephyrPipeHandler =
                channel.getPipeline().get(ZephyrPipeHandler.class);
        zephyrPipeHandler.setRemoteZid(remoteZid);
        zephyrPipeHandler.getReceiveLocalZidFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (!future.isSuccess()) {
                    l.warn("failed to connect over zephyr", future.getCause());
                    return;
                }

                l.debug("connected over zephyr!");

                sendConnectMessage(conn, zephyrPipeHandler.getLocalZid(),
                        zephyrPipeHandler.getRemoteZid(), message);
            }
        });
    }

    private static void sendConnectMessage(Connection conn,
            int localZid, int remoteZid, Message original)
    {
        PBTPHeader header = PBTPHeader.newBuilder()
                .setType(Type.ZEPHYR_CANDIDATE_INFO)
                .setZephyrInfo(PBZephyrCandidateInfo.newBuilder()
                        .setSourceZephyrId(localZid)
                        .setDestinationZephyrId(remoteZid)
                ).build();

        TransportDataExtension data = new TransportDataExtension();
        data.setProto(header);
        Message reply = new Message();
        reply.setFrom(original.getTo());
        reply.setTo(original.getFrom());
        reply.setSubject(MOBILE_SUBJECT);
        if (original.getThread() != null) {
            reply.setThread(original.getThread());
        }
        reply.addExtension(data);
        conn.sendPacket(reply);

        l.debug("sent packet to " + reply.getTo());
    }

    private void processIQ(final Connection connection, final TransportDataIQ iq)
    {
        final TransportDataIQ error = new TransportDataIQ(new TransportDataExtension());
        error.setTo(iq.getFrom());
        error.setFrom(iq.getTo());
        error.setPacketID(iq.getPacketID());
        error.setType(IQ.Type.ERROR);
        boolean ok = false;
        try {
            PBTPHeader header = iq.getData().getProto();
            if (header != null) {
                if (header.getType() == Type.ZEPHYR_CANDIDATE_INFO &&
                        header.hasZephyrInfo()) {
                    PBZephyrCandidateInfo zephyrInfo = header.getZephyrInfo();
                    int remoteZid = zephyrInfo.getSourceZephyrId();

                    ChannelFuture future = _bootstrap.connect(getZephyrAddress());
                    final ZephyrPipeHandler zephyrPipeHandler =
                            future.getChannel().getPipeline().get(ZephyrPipeHandler.class);
                    zephyrPipeHandler.setRemoteZid(remoteZid);
                    zephyrPipeHandler.getReceiveLocalZidFuture().addListener(
                            new ChannelFutureListener()
                            {
                                @Override
                                public void operationComplete(ChannelFuture future)
                                        throws Exception
                                {
                                    if (!future.isSuccess()) {
                                        l.warn("failed to connect over zephyr",
                                                future.getCause());
                                        connection.sendPacket(error);
                                        return;
                                    }

                                    l.debug("connected over zephyr!");

                                    TransportDataIQ response =
                                            new TransportDataIQ(new TransportDataExtension());
                                    response.setTo(iq.getFrom());
                                    response.setFrom(iq.getTo());
                                    response.setPacketID(iq.getPacketID());
                                    response.setType(IQ.Type.RESULT);
                                    PBTPHeader header = PBTPHeader.newBuilder()
                                            .setType(Type.ZEPHYR_CANDIDATE_INFO)
                                            .setZephyrInfo(PBZephyrCandidateInfo.newBuilder()
                                                    .setSourceZephyrId(
                                                            zephyrPipeHandler.getLocalZid())
                                                    .setDestinationZephyrId(
                                                            zephyrPipeHandler.getRemoteZid())).build();
                                    response.getData().setProto(header);
                                    connection.sendPacket(response);
                                }
                            });
                    ok = true;
                }
            }
        } finally {
            if (!ok) {
                connection.sendPacket(error);
            }
        }
    }
}
