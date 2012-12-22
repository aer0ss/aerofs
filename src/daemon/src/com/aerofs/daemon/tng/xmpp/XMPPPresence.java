/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.IPresenceListener;
import com.aerofs.daemon.tng.base.IPeerPresenceListener;
import com.aerofs.daemon.tng.base.IPresenceService;
import com.aerofs.daemon.tng.xmpp.XMPPServerConnectionService.IXMPPServerConnectionListener;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.lib.notifier.IListenerVisitor;
import com.aerofs.lib.notifier.Notifier;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableSet;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * XMPPBasedTransportFactory message body format (for both unicast and multicast)
 * <p/>
 * +--------------+-----+------+--------+ | MAGIC_NUMBER | len | data | chksum |
 * +--------------+-----+------+--------+
 */
final class XMPPPresence implements IPresenceService, IXMPPServerConnectionListener
{
    private static final Logger l = Util.l(XMPPPresence.class);

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final Notifier<IPeerPresenceListener> _peerPresenceNotifier = Notifier.create();
    private final Notifier<IPresenceListener> _transportPresenceNotifier = Notifier.create();
    private final XMPPPresenceStore _presences = new XMPPPresenceStore();
    private final AtomicBoolean _started = new AtomicBoolean(false);

    static XMPPPresence getInstance_(ISingleThreadedPrioritizedExecutor executor,
            XMPPServerConnectionService xmppServerConnectionService,
            ILinkStateService networkLinkStateService)
    {
        XMPPPresence presenceService = new XMPPPresence(executor);

        xmppServerConnectionService.addListener_(presenceService);
        networkLinkStateService.addListener_(presenceService, executor);

        return presenceService;
    }

    private XMPPPresence(ISingleThreadedPrioritizedExecutor executor)
    {
        this._executor = executor;
    }

    @Override
    public void start_()
    {
        boolean previouslyStarted = _started.getAndSet(true);
        if (previouslyStarted) return;

        l.info("start: noop");
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        l.info("lsc: noop");
    }

    @Override
    public void addListener_(IPresenceListener listener, Executor notificationExecutor)
    {
        assert !_started.get();

        _transportPresenceNotifier.addListener(listener, notificationExecutor);
    }

    @Override
    public void addListener_(IPeerPresenceListener listener, Executor notificationExecutor)
    {
        assert !_started.get();

        _peerPresenceNotifier.addListener(listener, notificationExecutor);
    }

    @Override
    public void xmppServerDisconnected()
    {
        l.warn("xmpp server disconnected");

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                handleXmppServerDisconnection_();
            }
        });
    }

    private void handleXmppServerDisconnection_()
    {
        l.info("delete all presence info");

        _presences.delall();

        l.info("notifying peer presence listeners xmpp server offline");

        _peerPresenceNotifier.notifyOnOtherThreads(new IListenerVisitor<IPeerPresenceListener>()
        {
            @Override
            public void visit(IPeerPresenceListener listener)
            {
                listener.onPresenceServiceDisconnected_();
            }
        });

        l.info("notifying transport presence listeners all peers offline");

        _transportPresenceNotifier.notifyOnOtherThreads(new IListenerVisitor<IPresenceListener>()
        {
            @Override
            public void visit(IPresenceListener listener)
            {
                listener.onAllPeersOffline();
            }
        });
    }

    @Override
    public void xmppServerConnected(XMPPConnection smackConnection)
            throws XMPPException
    {
        // adding the packet processor _has_ to be done inline

        l.info("adding presence packet processor inline for conn:" + smackConnection);

        smackConnection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(final Packet packet)
            {
                assert packet instanceof Presence : ("unexpected message type:" +
                                                             packet.getClass().getSimpleName());

                _executor.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try {
                            processPresence_((Presence) packet);
                        } catch (Exception e) {
                            l.warn("fail process presence from " + packet.getFrom() +
                                    " err:" + Util.e(e, ExFormatError.class));
                        }
                    }
                });

            }
        }, new PacketTypeFilter(Presence.class));

        // run server connection tasks that don't have to be on the smack thread

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                handleXmppServerConnection_();
            }
        });
    }

    private void handleXmppServerConnection_()
    {
        l.info("notifiying peer presence listeners xmpp server online");

        _peerPresenceNotifier.notifyOnOtherThreads(new IListenerVisitor<IPeerPresenceListener>()
        {
            @Override
            public void visit(IPeerPresenceListener listener)
            {
                listener.onPresenceServiceConnected_();
            }
        });
    }

    private void processPresence_(Presence p)
            throws ExFormatError
    {
        String[] tokens = ID.tokenize(p.getFrom());
        if (!ID.isMUCAddress(tokens)) return;

        final SID sid = ID.muc2sid(tokens[0]);
        final DID did = ID.user2did(tokens[1]);
        if (did.equals(Cfg.did())) return;

//        if (p.isAvailable()) {
//            _presences.add(did, sid);
//            _pm.stopPulse(did, false);
//        } else {
//            _spf.disconnect_(did, new Exception("remote offline"));
//            boolean waslast = _presences.del(did, sid);
//            if (waslast) _pm.stopPulse(did, false);
//        }

        final boolean online = p.isAvailable();

        if (online) {
            boolean wasfirst = _presences.add(did, sid);

            if (wasfirst) {
                l.info(did + ": presence service recv online presence");

                _peerPresenceNotifier.notifyOnOtherThreads(
                        new IListenerVisitor<IPeerPresenceListener>()
                        {
                            @Override
                            public void visit(IPeerPresenceListener listener)
                            {
                                listener.onPeerOnline_(did);
                            }
                        });
            }
        } else {
            boolean waslast = _presences.del(did, sid);

            if (waslast) {
                l.info(did + ": presence service recv offline presence");

                _peerPresenceNotifier.notifyOnOtherThreads(
                        new IListenerVisitor<IPeerPresenceListener>()
                        {
                            @Override
                            public void visit(IPeerPresenceListener listener)
                            {
                                listener.onPeerOffline_(did);
                            }
                        });
            }
        }

        _transportPresenceNotifier.notifyOnOtherThreads(new IListenerVisitor<IPresenceListener>()
        {
            @Override
            public void visit(IPresenceListener listener)
            {
                if (online) {
                    listener.onPeerOnline(did, ImmutableSet.of(sid));
                } else {
                    listener.onPeerOffline(did, ImmutableSet.of(sid));
                }
            }
        });
    }

    @Override
    public void dumpStat(PBDumpStat template, Builder bd)
            throws Exception
    {
        // noop for us
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // FIXME: dump the contents of presences
    }
}
