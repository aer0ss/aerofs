package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EITransportMetricsUpdated;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.exception.ExBadCRC;
import com.aerofs.daemon.transport.lib.CRCByteArrayInputStream;
import com.aerofs.daemon.transport.lib.CRCByteArrayOutputStream;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.labeling.L;
import com.aerofs.lib.C;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.AddressUtils.getinetaddr;

// TODO: checksum

/* multicast header:
 *
 * +-----------------+------------+---
 * | TCP_MCAST_MAGIC | PBTPHeader | payload (if and only if type == payload
 * +-----------------+------------+---
 */

class Multicast implements IMaxcast
{
    private static final Logger l = Util.l(Multicast.class);

    private final TCP t;
    private final Map<NetworkInterface, MulticastSocket> _iface2sock =
        Collections.synchronizedMap(new HashMap<NetworkInterface, MulticastSocket>());

    Multicast(TCP tcp)
    {
        this.t = tcp;
    }

    void init_() throws IOException
    {
        // send offline notification on exiting
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    sendControlMessage(TCP.newGoOfflineMessage());
                } catch (IOException e) {
                    l.warn("error sending offline notification. ignored" + e);
                }
            }
        }));
    }

    void start_()
    {
        // we don't dynamically detect preferred multicast size (TODO?)
        t.sink().enqueueBlocking(new EITransportMetricsUpdated(
                DaemonParam.TCP.MCAST_MAX_DGRAM_SIZE
                    - Integer.SIZE / 8          // for magic
                    - 1                         // for core_message
                    - DID.LENGTH        // for device id
                ), Prio.LO);
    }

    private void close(MulticastSocket s)
    {
        try {
            s.leaveGroup(InetAddress.getByName(L.get().mcastAddr()));
            s.close();
        } catch (Exception e) {
            l.warn("error closing mcast socket. ignored: " + e);
        }
    }

    void linkStateChanged(Set<NetworkInterface> added, Set<NetworkInterface> removed,
            boolean becameLinkDown)
    {
        for (NetworkInterface iface : added) {
            try {
                if (!iface.supportsMulticast()) continue;

                // bind to *:TCP_MCAST_PORT
                final MulticastSocket s = new MulticastSocket(L.get().mcastPort());
                s.setNetworkInterface(iface);
                s.setLoopbackMode(!Cfg.staging());
                s.joinGroup(InetAddress.getByName(L.get().mcastAddr()));

                MulticastSocket old = _iface2sock.put(iface, s);
                if (old != null) close(old);

                l.info("linkStateChanged->mc:add:" + iface.getName());

                new Thread(new Runnable() {
                    @Override
                    public void run()
                    {
                        thdRecv(s);
                    }
                }, t.id() + "-mcast.recv." + iface.getName()).start();

            } catch (IOException e) {
                l.warn("can't add mcast iface: " + e);
                continue;
            }
        }

        if (!added.isEmpty()) {
            try {
                sendControlMessage(TCP.newPingMessage());
                PBTPHeader pong = t.ss().newPongMessage(true);
                if (pong != null) sendControlMessage(pong);
            } catch (IOException e) {
                l.warn("send ping or pong: " + Util.e(e));
            }
        }

        if (becameLinkDown) {
            // We don't have to send offline messages if the the links are physically down. But in
            // case of a logical mark-down (LinkStateService#markLinksDown_()), we need manual
            // disconnection. N.B. this needs to be done *before* closing the sockets.

            try {
                sendControlMessage(TCP.newGoOfflineMessage());
            } catch (IOException e) {
                l.warn("send offline: " + Util.e(e));
            }
        }

        for (NetworkInterface iface : removed) {
            MulticastSocket s = _iface2sock.remove(iface);
            if (s == null) continue;

            l.info("linkStateChanged->mc:rem:");
            l.info(iface);

            close(s);
        }

        l.debug("mc:current ifs:");
        Set<Map.Entry<NetworkInterface, MulticastSocket>> entries = _iface2sock.entrySet();
        int i = 0;
        for (Map.Entry<NetworkInterface, MulticastSocket> e : entries) {
            int sval = (e.getValue() == null ? 0 : 1);
            l.debug("if" + i + ": " + e.getKey().getDisplayName() + "s:" + sval);
            l.debug(e.getKey());
            i++;
        }
    }

    private void thdRecv(MulticastSocket s)
    {
        while (true) {
            try {
                byte buf[] = new byte[DaemonParam.TCP.MCAST_MAX_DGRAM_SIZE];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                s.receive(pkt);

                //_bytesIn += pkt.getLength();

                CRCByteArrayInputStream is;
                try {
                    is = new CRCByteArrayInputStream(
                            pkt.getData(), pkt.getOffset(), pkt.getLength());
                } catch (ExBadCRC e) {
                    l.warn("bad crc from " + pkt.getSocketAddress());
                    continue;
                }

                // read magic
                int pktmagic = new DataInputStream(is).readInt();
                if (pktmagic != C.CORE_MAGIC) {
                    l.warn("magic mismatch exp:" + C.CORE_MAGIC + " act:" + pktmagic);
                    continue;
                }

                // read header
                PBTPHeader h = PBTPHeader.parseDelimitedFrom(is);

                assert h.hasTcpMulticastDeviceId();

                // ignore messages from myself
                DID did = new DID(h.getTcpMulticastDeviceId());
                if (did.equals(Cfg.did())) continue;
                Endpoint ep = new Endpoint(t, did);

                if (h.getType() == Type.DATAGRAM)
                {
                    assert h.hasMcastId() && h.hasSid();
                    // filter packets from core that were sent on other interface
                    if (false == t.mcfr().isRedundant(did, h.getMcastId()))
                    {
                        t.sink().enqueueThrows(
                                new EIMaxcastMessage(new Endpoint(t, did),
                                        new SID(h.getSid()), is, pkt.getLength()),
                                        Prio.LO);
                    }
                } else {
                    PBTPHeader ret = t.processMulticastControl(getinetaddr(pkt), ep, h);
                    if (ret != null) sendControlMessage(ret);
                }

            } catch (Exception e) {
                l.error("thdRecv(): " + Util.e(e, SocketException.class));
                if (!s.isClosed()) {
                    l.info("retry in " + DaemonParam.TCP.RETRY_INTERVAL + " ms");
                    ThreadUtil.sleepUninterruptable(DaemonParam.TCP.RETRY_INTERVAL);
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
        PBTPHeader h = PBTPHeader.newBuilder()
            .setType(Type.DATAGRAM)
            .setSid(sid.toPB())
            .setMcastId(mcastid)
            .setTcpMulticastDeviceId(Cfg.did().toPB())
            .build();
        send(h, buf);
    }

    void sendControlMessage(PBTPHeader h) throws IOException
    {
        assert h.hasTcpMulticastDeviceId();
        send(h, null);
    }

    private synchronized void send(PBTPHeader h, byte[] buf) throws IOException
    {
        CRCByteArrayOutputStream cos = new CRCByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(cos);
        try {
             // magic
            dos.writeInt(C.CORE_MAGIC);
            dos.flush();


            h.writeDelimitedTo(cos);

            if (h.getType() == Type.DATAGRAM) {
                cos.write(buf);  // TODO: avoid copying
            } else {
                assert buf == null;
            }

            byte[] bs = cos.toByteArray();

            DatagramPacket pkt = new DatagramPacket(bs, bs.length,
                                 InetAddress.getByName(L.get().mcastAddr()),
                                 L.get().mcastPort());
            //_bytesOut += bs.length;

            synchronized (_iface2sock) {
                for (MulticastSocket s : _iface2sock.values()) {
                    try {
                        if (l.isDebugEnabled()) {
                            l.debug("beg mc send:" + h.getType().name() + " s:" + pkt.getSocketAddress());
                        }

                        s.send(pkt);
                    } catch (IOException e) {
                        l.error("cannot send mc via: " + s.getNetworkInterface().getDisplayName() +
                                "(" + s.getLocalAddress() + ")");
                        l.error("addresses: ");

                        Enumeration<InetAddress> ias = s.getNetworkInterface().getInetAddresses();
                        InetAddress ia;
                        while (ias.hasMoreElements()) {
                            ia = ias.nextElement();
                            l.error("    " + ia.toString());
                        }

                        throw e;
                    }
                }
            }
        } finally {
            if (dos != null) dos.close();
        }
    }
}
