package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.exception.ExBadCRC;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.CRCByteArrayInputStream;
import com.aerofs.daemon.transport.lib.CRCByteArrayOutputStream;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newConcurrentMap;

// FIXME (AG): Remove call from Stores and make final
class Multicast implements IMaxcast, ILinkStateListener
{
    private static final Logger l = Loggers.getLogger(Multicast.class);

    private final DID localdid;
    private final TCP tcp;
    private final boolean listenToMulticastOnLoopback;
    private final MaxcastFilterReceiver maxcastFilterReceiver;
    private final Map<NetworkInterface, MulticastSocket> ifaceToMulticastSocket = newConcurrentMap();
    private final int multicastPort;
    private final String multicastAddress;
    private final int maxMulticastDatagramSize;
    private final int multicastTTL;
    private final long multicastReconnectInterval;

    private Stores stores; // the only reason this isn't final is because of a circular dependency between the two
    private IMulticastListener multicastListener;

    Multicast(DID localdid, TCP tcp, boolean listenToMulticastOnLoopback, MaxcastFilterReceiver maxcastFilterReceiver)
    {
        this.localdid = localdid;
        this.tcp = tcp;
        this.listenToMulticastOnLoopback = listenToMulticastOnLoopback;
        this.maxcastFilterReceiver = maxcastFilterReceiver;

        // FIXME (AG): expose all these parameters
        multicastPort = DaemonParam.TCP.MCAST_PORT;
        multicastAddress = DaemonParam.TCP.MCAST_ADDRESS;
        maxMulticastDatagramSize = DaemonParam.TCP.MCAST_MAX_DGRAM_SIZE;
        multicastTTL = DaemonParam.TCP.IP_MULTICAST_TTL;
        multicastReconnectInterval = DaemonParam.TCP.RETRY_INTERVAL;
    }

    public void setStores(Stores stores)
    {
        this.stores = stores;
    }

    public void setListener(IMulticastListener multicastListener)
    {
        this.multicastListener = multicastListener;
    }

    void init() throws IOException
    {
        // send offline notification on exiting
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    sendControlMessage(newGoOfflineMessage());
                } catch (IOException e) {
                    l.warn("error sending offline notification. ignored" + e);
                }
            }
        }));
    }

    private PBTPHeader newGoOfflineMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_GO_OFFLINE)
                .setTcpMulticastDeviceId(localdid.toPB())
                .build();
    }

    private void close(MulticastSocket s)
    {
        try {
            s.leaveGroup(InetAddress.getByName(multicastAddress));
            s.close();
        } catch (Exception e) {
            l.warn("error closing mcast socket. ignored: " + e);
        }
    }

    @Override
    public void onLinkStateChanged(
            ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed)
    {
        for (NetworkInterface iface : removed) {
            MulticastSocket s = ifaceToMulticastSocket.remove(iface);
            if (s == null) continue;
            l.info("lsc del mc {}", iface);
            close(s);
        }

        for (NetworkInterface iface : added) {
            try {
                if (!iface.supportsMulticast()) continue;

                final MulticastSocket s = new MulticastSocket(multicastPort); // bind to *:TCP_MCAST_PORT
                // N.B. Setting loopback mode to true _disables_ TCP multicast on local loopback
                // See http://docs.oracle.com/javase/6/docs/api/java/net/MulticastSocket.html#setLoopbackMode(boolean)
                s.setLoopbackMode(!listenToMulticastOnLoopback);
                s.joinGroup(new InetSocketAddress(multicastAddress, multicastPort), iface); // FIXME (AG): java api docs have this doing InetAddress.getByName

                MulticastSocket old = ifaceToMulticastSocket.put(iface, s);
                if (old != null) close(old);

                l.info("lsc add mc {}", iface.getName());

                new Thread(TransportThreadGroup.get(), new Runnable() {
                    @Override
                    public void run()
                    {
                        thdRecv(s);
                    }
                }, "m-" + iface.getName()).start();

            } catch (IOException e) {
                l.warn("can't add mcast iface_name:{} inet_addr:{} iface_addr:{}",
                        iface,
                        iface.getInetAddresses(),
                        iface.getInterfaceAddresses(),
                        LogUtil.suppress(e.getCause(), java.net.SocketException.class)
                );
            }
        }

        if (!added.isEmpty()) {
            try {
                sendControlMessage(newPingMessage());
                PBTPHeader pong = stores.newPongMessage(true);
                if (pong != null) sendControlMessage(pong);
            } catch (IOException e) {
                l.warn("send ping or pong: " + Util.e(e));
            }
        }

        l.trace("mc:current ifs:");
        Set<Map.Entry<NetworkInterface, MulticastSocket>> entries = ifaceToMulticastSocket.entrySet();
        int i = 0;
        for (Map.Entry<NetworkInterface, MulticastSocket> e : entries) {
            int sval = (e.getValue() == null ? 0 : 1);
            l.trace("if{}: {} s:{} {}", i, e.getKey().getDisplayName(), sval, e.getKey());
            i++;
        }

        if (previous.isEmpty() && !current.isEmpty()) {
            multicastListener.onMulticastReady();
        } else if (!previous.isEmpty() && current.isEmpty()) {
            multicastListener.onMulticastUnavailable();
        }
    }

    /**
     * Generate a new <code>TCP_PING</code> message
     *
     * @return {@link PBTPHeader} of type <code>TCP_PING</code>
     */
    PBTPHeader newPingMessage()
    {
        return PBTPHeader.newBuilder()
                .setType(Type.TCP_PING)
                .setTcpMulticastDeviceId(localdid.toPB())
                .build();
    }

    private void thdRecv(MulticastSocket s)
    {
        while (true) {
            try {
                byte buf[] = new byte[maxMulticastDatagramSize];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                s.receive(pkt);

                CRCByteArrayInputStream is;
                try {
                    is = new CRCByteArrayInputStream(pkt.getData(), pkt.getOffset(), pkt.getLength());
                } catch (ExBadCRC e) {
                    l.warn("bad crc from " + pkt.getSocketAddress());
                    continue;
                }

                // read magic
                int pktmagic = new DataInputStream(is).readInt();
                if (pktmagic != LibParam.CORE_PROTOCOL_VERSION) {
                    l.warn("magic mismatch exp:" + LibParam.CORE_PROTOCOL_VERSION + " act:" + pktmagic);
                    continue;
                }

                // read header
                PBTPHeader h = PBTPHeader.parseDelimitedFrom(is);

                checkArgument(h.hasTcpMulticastDeviceId());

                // ignore messages from myself
                DID did = new DID(h.getTcpMulticastDeviceId());
                if (did.equals(localdid)) continue;

                if (h.getType() == Type.DATAGRAM)
                {
                    checkArgument(h.hasMcastId());
                    // filter packets from core that were sent on other interface
                    if (!maxcastFilterReceiver.isRedundant(did, h.getMcastId())) {
                        tcp.sink().enqueueThrows(new EIMaxcastMessage(new Endpoint(tcp, did), is, pkt.getLength()), Prio.LO);
                    }
                } else {
                    InetAddress rem = ((InetSocketAddress) pkt.getSocketAddress()).getAddress();
                    PBTPHeader ret = processMulticastControl(rem, did, h);
                    if (ret != null) sendControlMessage(ret);
                }
            } catch (Exception e) {
                l.error("thdRecv() err", LogUtil.suppress(e, SocketException.class));

                if (!s.isClosed()) {
                    l.info("retry in {} ms", multicastReconnectInterval);
                    ThreadUtil.sleepUninterruptable(multicastReconnectInterval);
                } else {
                    l.info("socket closed by lsc; exit");
                    return;
                }
            }
        }
    }

    @Override
    public void sendPayload(SID sid, int mcastid, byte[] buf)
            throws IOException
    {
        PBTPHeader h = PBTPHeader
                .newBuilder()
                .setType(Type.DATAGRAM)
                .setMcastId(mcastid)
                .setTcpMulticastDeviceId(localdid.toPB())
                .build();
        send(h, buf);
    }

    void sendControlMessage(PBTPHeader h) throws IOException
    {
        checkArgument(h.hasTcpMulticastDeviceId());
        send(h, null);
    }

    private synchronized void send(PBTPHeader h, byte[] buf) throws IOException
    {
        CRCByteArrayOutputStream cos = new CRCByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(cos);
        try {
            dos.writeInt(LibParam.CORE_PROTOCOL_VERSION);
            dos.flush();

            h.writeDelimitedTo(cos);

            if (h.getType() == Type.DATAGRAM) {
                cos.write(buf);  // TODO: avoid copying
            } else {
                checkState(buf == null);
            }

            byte[] bs = cos.toByteArray();

            DatagramPacket pkt = new DatagramPacket(bs, bs.length, InetAddress.getByName(multicastAddress), multicastPort);
            synchronized (ifaceToMulticastSocket) {
                for (Map.Entry<NetworkInterface, MulticastSocket> entry : ifaceToMulticastSocket.entrySet()) {
                    NetworkInterface iface = entry.getKey();
                    Enumeration<InetAddress> inetAddresses = iface.getInetAddresses();
                    MulticastSocket s = entry.getValue();

                    InetAddress sendingAddress;
                    while (inetAddresses.hasMoreElements()) {
                        sendingAddress = inetAddresses.nextElement();

                        try {
                            l.trace("attempt mc send iface:{} send_addr:{} dest_addr:{} t:{}",
                                    iface, sendingAddress, pkt.getSocketAddress(), h.getType().name());

                            s.setInterface(sendingAddress);
                            s.setTimeToLive(multicastTTL);
                            s.send(pkt);
                        } catch (IOException e) {
                            l.error("fail send mc iface:{} send_addr:{} dest_addr:{}", iface, sendingAddress, pkt.getSocketAddress());

                            throw e;
                        }
                    }
                }
            }
        } finally {
            dos.close();
        }
    }

    /**
     * Processes control messages received on a <code>tcp</code> unicast channel
     *
     *
     * @param rem {@link java.net.InetAddress} of the remote peer from which the message
     * @param did {@link com.aerofs.base.id.DID} from which the message was received
     * @param hdr {@link com.aerofs.proto.Transport.PBTPHeader} message that was received
     * @return PBTPHeader response to be sent as a control message to the remote peer
     * @throws com.aerofs.base.ex.ExProtocolError for an unrecognized control message
     */
    PBTPHeader processMulticastControl(InetAddress rem, DID did, PBTPHeader hdr)
        throws ExProtocolError
    {
        PBTPHeader ret = null;

        Type type = hdr.getType();
        switch (type)
        {
        case TCP_PING:
            ret = stores.processPing(true);
            break;
        case TCP_PONG:
            stores.processPong(rem, did, hdr.getTcpPong());
            break;
        case TCP_GO_OFFLINE:
            stores.processGoOffline(did);
            break;
        case TCP_NOP:
            break;
        default:
            throw new ExProtocolError("unrecognized multicast packet with type:" + type.name());
        }

        return ret;
    }
}
