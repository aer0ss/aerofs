package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.IPipeController;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.TCPProactorMT;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnection;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnectionManager;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnector;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IReactor;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.tcpmt.ARP.ARPEntry;
import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.proto.Files;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTCPUnicastPreamble;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;

import static com.aerofs.daemon.transport.lib.AddressUtils.getinetaddr;
import static com.aerofs.daemon.transport.lib.AddressUtils.printaddr;
import static com.aerofs.proto.Files.PBDumpStat.PBTransport;

public class Unicast implements IConnectionManager, IUnicast, IPipeDebug
{
    /**
     * @param internalPort null if port forwarding is not used. otherwise,
     * we listen to the internal port but getListingPort() returns the external
     * port.
     */
    Unicast(IPipeController pc, ARP arp, Stores ss, int port, Integer internalPort)
            throws IOException
    {
        // external port must be a specific value if internal port is specified
        assert internalPort == null || port != TCP.PORT_ANY;

        _pc = pc;
        _arp = arp;
        _ss = ss;
        _proactor = new TCPProactorMT("tp", this, null, internalPort == null ? port : internalPort,
                C.CORE_MAGIC, true, DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE);
        _port = internalPort == null ? _proactor.getListeningPort() : port;

        l.info("port " + _port + " internal " + _proactor.getListeningPort());
    }

    void start_()
    {
        _proactor.start_();
    }

    /**
     * Forcefully disconnects all connections to/from a remote address
     *
     * @param remaddr remote address for which all connections should be
     * peerDisconnected
     */
    void disconnect(InetSocketAddress remaddr)
    {
        _proactor.disconnect(remaddr);
    }

    /**
     * Whether we are connected to a remote address
     *
     * @param remaddr remote address to check for connections
     * @return true if we are connected, false if not
     */
    boolean isConnected(InetSocketAddress remaddr)
    {
        return _proactor.isConnected(remaddr);
    }

    int getListeningPort()
    {
        return _port;
    }

    @Override
    public long getBytesRx(DID did)
    {
        ARPEntry arpentry = _arp.get(did);
        return arpentry == null ? 0 : _proactor.getBytesRx(arpentry._isa);
    }

    private Collection<String> getConnections()
    {
        return _proactor.getConnections();
    }

    private long getBytesRx()
    {
        return _proactor.getBytesRx();
    }

    private long getBytesTx()
    {
        return _proactor.getBytesTx();
    }

    @Override
    public Object send_(DID did, IResultWaiter wtr, Prio pri, byte[][] bss, Object cke)
        throws ExDeviceOffline, ExNoResource, IOException
    {
        // use the address specified as the cookie to send the packet if the
        // cookie is present. this is to bind an outgoing stream to a particular
        // TCP connection, needed for the following scenario:
        //
        // 1. A and B have two or more ethernet links connected to each other.
        // 2. A receives B's pong message from one link, and add the IP address
        //    to its ARP
        // 3. A starts sending a stream to B
        // 4. A receives B's pong from another link, and in turn update the APR
        // 5. the rest of the chunks in the stream will be sent via the latter
        //    link, which violates streams' guarantee of in-order delivery.
        //
        InetSocketAddress addr = cke == null ? _arp.getThrows(did)._isa : (InetSocketAddress) cke;
        _proactor.send(addr, bss, did, wtr, pri);
        return addr;
    }

    public void sendControl(DID did, InetSocketAddress remaddr, PBTPHeader h, Prio prio)
        throws IOException, ExNoResource
    {
        byte[][] bss = TPUtil.newControl(h);
        _proactor.send(remaddr, bss, did, null, prio);
    }

    public void sendControl(DID did, PBTPHeader h, Prio prio)
        throws ExDeviceOffline, IOException, ExNoResource
    {
        InetSocketAddress remaddr = _arp.getThrows(did)._isa;
        sendControl(did, remaddr, h, prio);
    }

    @Override
    public IConnector newOutgoingConnection(IConnection c, InetSocketAddress to,
            Object cookie)
    {
        return new Connector((DID) cookie);
    }

    @Override
    public IReactor newIncomingConnection(IConnection c, InetSocketAddress from)
    {
        return new Reactor(c, from);
    }

    private byte[][] newPreamble()
    {
        PBTPHeader h = PBTPHeader.newBuilder()
            .setType(Type.TCP_UNICAST_PREAMBLE)
            .setTcpPreamble(PBTCPUnicastPreamble.newBuilder()
                    .setDeviceId(Cfg.did().toPB())
                    .setListeningPort(getListeningPort())).build();

        return TPUtil.newControl(h);
    }

    @Override
    public void dumpStat(Files.PBDumpStat dstemplate, Files.PBDumpStat.Builder dsbuilder)
        throws Exception
    {
        PBTransport tp = dstemplate.getTp(0);
        assert tp != null : ("dumpstat called with null tp template");

        // FIXME: this is broken - is there a better way to do this?

        int lastBuilderIdx = dsbuilder.getTpBuilderList().size();
        assert lastBuilderIdx >= 1 : ("must have been populated by parent dumpstat");

        PBTransport.Builder tpbuilder = dsbuilder.getTpBuilder(lastBuilderIdx - 1);

        tpbuilder.setBytesIn(getBytesRx());
        tpbuilder.setBytesOut(getBytesTx());

        if (tp.hasDiagnosis()) tpbuilder.setDiagnosis("arp:\n" + _arp);

        if (tp.getConnectionCount() != 0) {
            for (String c : getConnections()) {
                tpbuilder.addConnection(c);
            }
        }

        dsbuilder.setTp(lastBuilderIdx, tpbuilder);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        // empty - nothing to add to dumpStatMisc
    }

    //
    // types
    //

    //
    // Connector
    //

    private class Connector implements IConnector
    {
        Connector(DID did)
        {
            this.did = did;
        }

        @Override
        public byte[][] getConnectorPreamble_()
        {
            return newPreamble();
        }

        @Override
        public void connectorDisconnected_()
        {
            l.info("connector disconnected" + did);
            _pc.closePeerStreams(did, true, false);
        }

        private final DID did;
    }

    //
    // Reactor
    //

    private class Reactor implements IReactor
    {
        Reactor(IConnection c, InetSocketAddress remaddr)
        {
            _c = c;
            _printaddr = remaddr;
            _remaddr = getinetaddr(remaddr);

            l.info("reactor created rem: " + _remaddr);
        }

        @Override
        public byte[][] getReactorPreamble_()
        {
            return newPreamble();
        }

        @Override
        public byte[][] react_(byte[] bs, int wirelen) throws Exception
        {
            ByteArrayInputStream is = new ByteArrayInputStream(bs);
            PBTPHeader h = TPUtil.processUnicastHeader(is);

            PBTPHeader ret = null;
            Type type = h.getType();
            if (TPUtil.isPayload(h)) {
                if (_did == null) {
                    l.warn("payload before preamble rem:" + printaddr(_printaddr)+ " - discard");
                    return null;
                }

                _pc.processUnicastPayload(_did, h, is, wirelen); // silently discard data
            } else if (type == Type.TCP_UNICAST_PREAMBLE) {
                PBTCPUnicastPreamble preamble = h.getTcpPreamble();

                _did = new DID(preamble.getDeviceId());
                _remoteListeningPort = preamble.getListeningPort();

                if (l.isInfoEnabled()) {
                    l.info("recv preamble d:" + _did +
                        " rem:" + _remaddr + " l:" + _remoteListeningPort);
                }

                // reuse incoming connection for outgoing requests to the
                // peer's announced listening port
                InetSocketAddress isa = new InetSocketAddress(_remaddr, _remoteListeningPort);

                _proactor.reuseForOutgoingConnection(isa, _c);

                // for SP, send member stores only after receiving the peer's

                if (!Cfg.isSP()) {
                    ret = PBTPHeader.newBuilder()
                        .setType(Type.TCP_STORES)
                        .setTcpStores(_ss.newStoresForNonSP(_did))
                        .build();
                }
            } else if (type == Type.TCP_STORES) {
                if (_did == null) {
                    l.warn("payload before preamble rem:" + printaddr(_printaddr) + " - discard");
                    return null;
                }

                _ss.storesReceived(_did, _remaddr,
                    _remoteListeningPort, h.getTcpStores(), false);

                if (Cfg.isSP()) {
                    ARPEntry arpentry = _arp.get(_did);
                    if (arpentry == null || arpentry._prefixes == null) {
                        // don't send member stores if the peer doesn't provide
                        // the prefixes. so it's critical for non-SP client
                        // always send prefixes (not filters) to SP.
                        ret = null;
                    } else {
                        ret = PBTPHeader.newBuilder()
                                .setType(Type.TCP_STORES)
                                .setTcpStores(_ss.newStoresForSP(_did, arpentry._prefixes))
                                .build();
                    }
                }
            } else if (type == Type.TCP_PONG) {
                Transport.PBTCPPong pong = h.getTcpPong();
                _ss.filterReceived(_did, _remaddr,
                    pong.getUnicastListeningPort(), pong.getFilter(), false);
            } else {
                _pc.processUnicastControl(_did, h);
            }

            return ret == null ? null : TPUtil.newControl(ret);
        }

        @Override
        public void reactorDisconnected_()
        {
            l.info("reactor disconnected: d:" +
                (_did == null ? "null" : _did) +" rem:" + printaddr(_printaddr));

            if (_did != null) _pc.closePeerStreams(_did, false, true);
        }

        private final InetAddress _remaddr;
        private final InetSocketAddress _printaddr; // FIXME: remove this
        private final IConnection _c;

        private DID _did;
        private int _remoteListeningPort;
    }

    public void pauseAccept()
    {
        _proactor.pauseAccept();
    }

    public void resumeAccept()
    {
        _proactor.resumeAccept();
    }

    //
    // members
    //

    private final IPipeController _pc;
    private final ARP _arp;
    private final Stores _ss;
    private final TCPProactorMT _proactor;
    private final int _port;

    private static final Logger l = Util.l(Unicast.class);
}
