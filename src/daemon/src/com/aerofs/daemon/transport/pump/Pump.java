/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.pump;

import com.aerofs.base.BaseParam;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.debug.Tput;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.util.Arrays;

import static com.aerofs.base.ssl.SSLEngineFactory.Mode.Client;
import static com.aerofs.base.ssl.SSLEngineFactory.Mode.Server;
import static com.aerofs.base.ssl.SSLEngineFactory.Platform.Desktop;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.JINGLE;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.LANTCP;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.ZEPHYR;
import static com.aerofs.lib.ChannelFactories.getClientChannelFactory;
import static com.aerofs.lib.ChannelFactories.getServerChannelFactory;
import static com.aerofs.lib.event.Prio.LO;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public final class Pump implements IProgram
{
    private static final int CHUNK_SIZE = 10 * 1024;

    private static final Logger l = Loggers.getLogger(Pump.class);

    private final BlockingPrioQueue<IEvent> incomingEventSink = new BlockingPrioQueue<IEvent>(1024);
    private final ILinkStateService linkStateService = new SingleThreadedLinkStateService();

    private boolean isSender;
    private ITransport transport;
    private @Nullable DID remote;
    private volatile boolean keepRunning = true;
    private Tput tput = new Tput("recv");

    @Override
    public void launch_(String rtRoot, String prog, String[] args) // PROG RTROOT [t|z|j] [send|recv] <did>
            throws Exception
    {
        l.info(Arrays.toString(args));
        checkArgument(args.length == 2 || args.length == 3, String.format("usage: SEND:(%s [t|z|j] send [did]) RECV:(%s [t|z|j] recv)", prog, prog));

        isSender = args[1].equalsIgnoreCase("send");
        if (isSender) {
            checkArgument(args.length == 3, String.format("usage: SEND:(%s [t|z|j] send [did])", prog));
        }

        remote = (isSender ? new DID(DID.fromStringFormal(args[2])) : null);
        transport = newTransport(args[0]);
        final IIMCExecutor transportImce = new QueueBasedIMCExecutor(transport.q());

        // start transport

        linkStateService.addListener_(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged_(ImmutableSet<NetworkInterface> previous, ImmutableSet<NetworkInterface> current, ImmutableSet<NetworkInterface> added, ImmutableSet<NetworkInterface> removed)
            {
                transport.q().enqueueBlocking(new EOLinkStateChanged(transportImce, previous, current, added, removed), LO);
            }
        }, sameThreadExecutor());

        transport.init_();
        transport.start_();
        linkStateService.start_();

        // join the root store, so that I can actually receive presence info

        transport.q().enqueueBlocking(new EOUpdateStores(transportImce, new SID[]{Cfg.rootSID()}, new SID[]{}), LO);

        // start listening

        OutArg<Prio> eventPriority = new OutArg<Prio>(LO);
        while (true) {
            IEvent incoming = incomingEventSink.dequeue(eventPriority);
            if (incoming instanceof EIUnicastMessage) {
                handleUnicastMessage((EIUnicastMessage)incoming);
            } else if (incoming instanceof EIPresence) {
                handlePresence((EIPresence) incoming);
            } else {
                handleOther(incoming);
            }
        }
    }

    private ITransport newTransport(String transportId)
            throws ExUnsupportedTransport
    {
        TransportFactory transportFactory = newTransportFactory();

        if (transportId.equalsIgnoreCase("t")) {
            return transportFactory.newTransport(LANTCP);
        } else if (transportId.equalsIgnoreCase("z")) {
            Zephyr zephyr = (Zephyr) transportFactory.newTransport(ZEPHYR);
            zephyr.enableMulticast();
            return zephyr;
        } else if (transportId.equalsIgnoreCase("j")) {
            return transportFactory.newTransport(JINGLE);
        } else {
            throw new ExUnsupportedTransport(transportId);
        }
    }

    private TransportFactory newTransportFactory()
    {
        CfgAbsRTRoot absRTRoot = new CfgAbsRTRoot();
        CfgLocalUser localid = new CfgLocalUser();
        CfgLocalDID localdid = new CfgLocalDID();
        CfgScrypted scrypted = new CfgScrypted();
        RockLog rockLog = new RockLog();
        MaxcastFilterReceiver maxcastFilterReceiver = new MaxcastFilterReceiver();
        CfgKeyManagersProvider keyProvider = new CfgKeyManagersProvider();
        CfgCACertificateProvider trustedCA = new CfgCACertificateProvider();
        SSLEngineFactory clientSslEngineFactory = new SSLEngineFactory(Client, Desktop, keyProvider, trustedCA, null);
        SSLEngineFactory serverSslEngineFactory = new SSLEngineFactory(Server, Desktop, keyProvider, trustedCA, null);
        ClientSocketChannelFactory clientChannelFactory = getClientChannelFactory();
        ServerSocketChannelFactory serverChannelFactory = getServerChannelFactory();
        return new TransportFactory(
                absRTRoot,
                localid,
                localdid,
                scrypted,
                BaseParam.Zephyr.ADDRESS.get(),
                Proxy.NO_PROXY,
                incomingEventSink,
                rockLog,
                maxcastFilterReceiver,
                null,
                clientChannelFactory,
                serverChannelFactory,
                clientSslEngineFactory,
                serverSslEngineFactory);
    }

    private void handleUnicastMessage(EIUnicastMessage unicastMessage)
    {
        l.debug("recv incoming d:{}", unicastMessage._ep.did());
        tput.observe(unicastMessage.wireLength());
    }

    private void handlePresence(EIPresence presence)
    {
        if (presence._online && presence._did2sids.containsKey(remote)) {
            l.info("connected d:{}", remote);

            if (isSender) {
                Thread t = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        l.info("start send thd d:{}", remote);

                        while (keepRunning) {
                            transport.q().enqueueBlocking(new EOUnicastMessage(remote, new byte[CHUNK_SIZE]), LO);
                        }

                        l.info("stop send thd d:{}", remote);
                    }
                });

                t.start();
            }
        } else if (!presence._online && presence._did2sids.containsKey(remote)) {
            l.info("disconnected d:{}", remote);
            keepRunning = false;
            throw new RuntimeException("hackish");
        }
    }

    private void handleOther(IEvent incoming)
    {
        l.warn("ignore event:{}", incoming.getClass().getSimpleName());
    }
}
