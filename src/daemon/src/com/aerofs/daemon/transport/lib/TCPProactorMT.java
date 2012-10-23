package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.StrictLock;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoResource;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.aerofs.daemon.transport.lib.AddressUtils.*;

/**
 * I'm hating myself for writing such ugly code in this file. Gonna rewrite it
 * someday. First, use event-driven. Second, don't reuse incoming connection for
 * outgoing packets. Always create new connections for sending and shutdown the
 * receiving half -- Weihan
 *
 * FIXME: replace timing with crosscuts -- Allen
 */

/* Message header
 *
 * +--------------+-------------------+
 * | magic_number | length_of_payload |
 * +--------------+-------------------+
 */

/*
 * Mutexes used inside the class:
 *      _map:       protect _map. Owner mustn't block
 *      p.getLock() == p._sendq.getLock(). protect fields in the Peer class and
 *                  the event queue. Owner  mustn't block except for queue
 *                  operations
 *      Socket:     protect syncSend()
 *
 *      NB. locking order: _map then p.getLock() then Socket
 */

public class TCPProactorMT
{
    // names of reactor threads will be in the form of "<name>-reactor.*"
    // @param port set to zero to use any free port
    // @param addr set to null to accept connections on all local addresses.
    //             use C.LOCALHOST_ADDR to listen on localhost.
    //
    public TCPProactorMT(String name, IConnectionManager manager, InetAddress addr,
            int port, int magic, boolean sendable, int maxRecvMsgSize)
        throws IOException
    {
        _name = name;
        _manager = manager;
        try {
            _ss = new ServerSocket(port, DaemonParam.TCP.BACKLOG, addr);
        } catch (BindException e) {
            l.warn("error binding to " +addr + ":" + port);
            throw e;
        }
        _magic = magic;
        _sendable = sendable;
        _maxRecvMsgSize = maxRecvMsgSize;
    }

    public void start_()
    {
        l.info(_name + ": listening port " + getListeningPort());

        new Thread(new Runnable() {
            @Override
            public void run()
            {
                accept();
            }
        }, _name + "-reactor").start();
    }

    public int getListeningPort()
    {
        return _ss.getLocalPort();
    }

    /**
     * Stop accepting new connections regardless how many times resumeAccept() has been called
     * before.
     */
    public void pauseAccept()
    {
        _acceptPaused = true;
    }

    /**
     * Resume accepting new connections regardless how many times stopAccept() has been called
     * before.
     */
    public void resumeAccept()
    {
        _acceptPaused = false;
        synchronized (_resumeAcceptSynchronizer) {
            _resumeAcceptSynchronizer.notify();
        }
    }

    public Collection<String> getConnections()
    {
        synchronized (_map) {
            ArrayList<String> cs = new ArrayList<String>();
            for (Map.Entry<InetSocketAddress, Peer> en : _map.entrySet()) {
                assert en.getValue() != null: ("invalid map entry");

                if (en.getValue()._discarded) continue;
                cs.add(getConnectionString(en));
            }
            return cs;
        }
    }

    public long getBytesRx()
    {
        return _bytesrx.get();
    }

    public long getBytesTx()
    {
        return _bytestx.get();
    }

    public long getBytesRx(InetSocketAddress isa)
    {
        synchronized (_map) {
            Peer p = _map.get(isa);
            return p == null ? 0 : p._bytesrx;
        }
    }

    public boolean isConnected(InetSocketAddress isa)
    {
        synchronized (_map){
            return _map.containsKey(isa);
        }
    }

    private void accept()
    {
        while (true) {
            try {
                Socket s = _ss.accept();

                if (_acceptPaused) {
                    s.close();
                    synchronized (_resumeAcceptSynchronizer) {
                        while (_acceptPaused) Util.waitUninterruptable(_resumeAcceptSynchronizer);
                    }
                }

                InetSocketAddress remaddr = getaddr(s, false);
                InetSocketAddress locaddr = getaddr(s, true);

                l.info(_name + ": accepted conn rem:" + remaddr + " locport:" + locaddr.getPort());

                Peer p = new Peer(s);
                receive(p, s, false);
            } catch (IOException e) {
                l.error(_name + ": error accepting connections: " + e);
                break;
            }
        }
    }

    /**
     * Forcefully disconnect connections to/from a remote address
     *
     * @param from connections with this remote address will be terminated
     */
    public void disconnect(InetSocketAddress from)
    {
        Peer p;
        synchronized (_map) {
            if (!_map.containsKey(from)) return;
            p = _map.get(from);
        }

        discard(p, new Exception("forceful close"));
    }

    // reuse c when next time send(ep, ...) is called.
    // can call this method only once for each c.
    //
    public void reuseForOutgoingConnection(InetSocketAddress remaddr, IConnection c)
        throws IOException
    {
        l.info("attempt conn reuse: rem:" + printaddr(remaddr));

        synchronized (_map) {
            if (_map.containsKey(remaddr)) {
                l.info("sender thread already created: rem:" + printaddr(remaddr) + " ignored");
                return;
            }

            Peer p = (Peer) c;
            if (p._discarded) throw new IOException("sender already discarded");

            // needn't sync(p.lock()) here as the object hasn't been shared yet

            // I believe this is still true. Basically, the only way you can get
            // past the previous checks is becase the sender hasn't put the key
            // in the map (in which case there is no sender); in this case the
            // Peer object was created by accept() and it really hasn't been shared
            // yet

            assert p._key == null;
            p._key = remaddr;
            _map.put(remaddr, (Peer) c);
        }
    }

    // BUGBUG this is called by both receiving and sending methods. The side
    // effect of this is newIncomingConnection may be called twice for the same
    // peer
    private void receive(final Peer p, final Socket s, final boolean borrowed)
    {
        new Thread(new Runnable() {
            @Override
            public void run()
            {
                thdRecv(p, s, borrowed);
            }
        }, _name + "-recv-" + printsock(s, false) + "-" + socketcounter.getAndIncrement()).start();
    }

    private void thdRecv(Peer p, Socket s, boolean borrowed)
    {
        InetSocketAddress from = getaddr(s, false);

        l.info("create recv thd:" + printaddr(from));

        IReactor rtr = null;
        try {
            rtr = _manager.newIncomingConnection(p, from);
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));
            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));

            if (!borrowed) {
                byte[][] preamble = rtr.getReactorPreamble_();
                if (preamble != null) syncSend(s, out, preamble);
            }

            while (true) {
                // receive the message
                byte[] bs = Util.readMessage(in, _magic, _maxRecvMsgSize);
                int wirelen = bs.length + Integer.SIZE * 2;
                _bytesrx.addAndGet(wirelen);
                p._bytesrx += wirelen;

                if (l.isDebugEnabled()) {
                    l.debug("recv fin:" + getTransferString_(s, true) + " b:" + wirelen);
                }

                // process_ the message
                byte[][] bss = rtr.react_(bs, wirelen);

                // send back reply
                if (bss != null) syncSend(s, out, bss);
            }

        } catch (EOFException e) {
            l.info(s.getRemoteSocketAddress() + " closed conection");
            discard(p, e);
        } catch (Exception e) {
            l.warn("error receiving from " + s.getRemoteSocketAddress() +
                    ". close the socket: " + Util.e(e, IOException.class));
            discard(p, e);
        }

        if (rtr != null) rtr.reactorDisconnected_();
    }

    public void send(final InetSocketAddress remaddr, byte[][] bss,
            final Object cookie, IResultWaiter waiter, Prio prio)
        throws ExNoResource, IOException
    {
        assert _sendable;

        Peer p = null;
        synchronized (_map) {
            p = _map.get(remaddr);
            if (p == null) {
                p = new Peer(null);
                p._key = remaddr;
                _map.put(remaddr, p);
            }
        }

        // can't nest this critical section within sync (_map) above because
        // we may block during enqueueBlocking below
        p.getLock().lock();
        try {
            if (p._discarded) {
                // discard(p) was called just before we locked p.getLock().
                throw new IOException("the peer has been discarded");
            }

            if (p._sendthd == null) {
                final Peer p2 = p;
                p._sendthd = new Thread(new Runnable() {
                    @Override
                    public void run()
                    {
                        thdSend(p2, remaddr, cookie);
                    }
                }, _name + "-send-" + printaddr(remaddr) + "-" + socketcounter.getAndIncrement());

                p._sendthd.start();
            }

            p._sendq.enqueueThrows_(new EvSend(bss, waiter), prio);
        } finally {
            p.getLock().unlock();
        }
    }

    /**
     * Sends bytes over a socket that may (or may not) be shared between sending
     * and receiving threads. Since multiple threads may contend over the sockets
     * for sending we have to lock it prior to the send.
     *
     * @param s socket over which bytes are to be sent (functions as lock object)
     * @param out {@link DataOutputStream} that wraps this socket
     * @param bss bytes to be sent
     * @return number of bytes sent
     * @throws Exception if there was any sort of problem while sending the data
     */
    private long syncSend(Socket s, DataOutputStream out, byte[][] bss)
        throws Exception
    {
        int bytesSent;
        long sendBegin = System.currentTimeMillis();
        synchronized (s) {
            bytesSent = Util.writeMessage(out, _magic, bss);
            _bytestx.addAndGet(bytesSent);
        }
        if (l.isDebugEnabled()) {
            l.debug("send fin:" + getTransferString_(s, false) + " b:" + bytesSent + " t:" +
                    (System.currentTimeMillis() - sendBegin));
        }

        return bytesSent;
    }

    private void thdSend(Peer p, InetSocketAddress remaddr, Object cookie)
    {
        l.info("create send thd:" + printaddr(remaddr));

        Socket os = null;
        boolean borrowed;
        IOException ex = null;
        if (p._is == null) {
            borrowed = false;
            try {
                //l.info(_name + ": connecting to " + ep);
                os = new Socket();
                os.setSoLinger(true, 0);
                os.connect(remaddr, (int) Cfg.timeout());

                InetSocketAddress locaddr = getaddr(os, true);

                l.info("connected to " + remaddr + " at local port " +
                        locaddr.getPort());

            } catch (IOException e) {
                // this produces too much log when SP is offline
                //l.warn(_name + ": cannot connect to " + ep + ": " + e);
                ex = e;
            }
        } else {
            borrowed = true;
            l.info("reuse incoming socket for outgoing pkts to " + remaddr);
            os = p._is;
        }

        if (ex == null) {
            assert os != null;
            p.getLock().lock();
            try {
                p._os = os;
            } finally {
                p.getLock().unlock();
            }
            if (!borrowed) receive(p, os, true);
        } else {
            discard(p, ex);
            return;
        }

        IConnector ctr = null;
        try {
            ctr = _manager.newOutgoingConnection(p, remaddr, cookie);
            assert ctr != null;

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(os.getOutputStream()));

            if (!borrowed) {
                byte [][] preamble = ctr.getConnectorPreamble_();
                if (preamble != null) syncSend(os, out, preamble);
            }

            OutArg<Prio> outPrio = new OutArg<Prio>();
            while (true) {
                l.debug("wait evsend");
                long evwtbeg = System.currentTimeMillis();
                EvSend ev = (EvSend) p._sendq.dequeue(outPrio);
                l.debug("ev wt t:" + (System.currentTimeMillis() - evwtbeg));

                if (ev == EV_CLOSE) break;

                try {
                    syncSend(os, out, ev._bss);
                    if (ev._waiter != null) ev._waiter.okay();
                } catch (IOException e) {
                    if (ev._waiter != null) {
                        l.debug("notify " + ev._waiter + " sending error: " + e);
                        ev._waiter.error(e);
                    }
                    throw e;
                }
            }
        } catch (Exception e) {
            l.warn("error sending to " + p._key + ". close the socket: " + e + " intr:" +
                    Thread.interrupted());
            l.warn(Util.stackTrace2string(e));
            discard(p, e);
        } finally {
            // discard() simply interrupts the thread and lets this thread attempt
            // to close the socket. I do this because attempting to close from the
            // main TCP thread can sometimes hang the transport thread completely
            // since the socket may take a long time to close
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    l.warn("err closing sender to rem:" + printaddr(p._key) + " err:" + Util.e(e));
                }
            }
        }

        if (ctr != null) ctr.connectorDisconnected_();
    }

    private void discard(Peer p, Exception ex)
    {
        l.info("discard: " + (p._key == null ? "null" : p._key));

        Socket is, os;

        // N.B. locking order throughout the code: must always be _map first
        // and p second.
        synchronized (_map) {
            p.getLock().lock();
            try {

                // drain the send queue. we place the code here as the task
                // has to be done before the return statement below.
                //
                // this task as well as the enqueueing task below must be atomic
                // with the p._discarded = true statement, otherwise new packets
                // may be inserted into the queue.
                //
                OutArg<Prio> outPrio = new OutArg<Prio>();
                while (true) {
                    EvSend ev = (EvSend) p._sendq.tryDequeue_(outPrio);
                    if (ev == null) break;
                    if (ev._waiter != null) {
                        l.debug("notify " + ev._waiter + " to drain send q");
                        ev._waiter.error(new IOException("draining send q"));
                    }
                }

                // enqueue the close event regardless if it's sending or recving
                // thread calling this method
                try {
                    p._sendq.enqueueThrows_(EV_CLOSE, Prio.HI);
                } catch (ExNoResource e) {
                    Util.fatal(e);
                }

                if (p._discarded) return;
                p._discarded = true;

                if (p._key != null) {
                    Util.verify(_map.remove(p._key) == p);
                }

                is = p._is;
                os = p._os;
            } finally {
                p.getLock().unlock();
            }
        }

        l.debug("discard: close sockets");

        try {
            // this causes the threads receiving/sending at the socket to fail
            if (is != null) {
                if (is != os) {
                    l.debug("close in socket at local port " + is.getLocalPort());
                    is.close();
                } else {
                    l.debug("close input half of socket at local port " + is.getLocalPort());
                    is.shutdownInput();
                }
            }

            if (os != null) {
                p._sendthd.interrupt();
                l.debug("interrupt thd to close socket at local port " + os.getLocalPort());
            }
        } catch (IOException e) {
            l.warn("ignored: " + Util.e(e, SocketException.class));
        }

        l.info("discard: sockets closed");
    }

    public void send(InetSocketAddress remaddr, byte[][] bss, Prio prio)
        throws IOException, ExNoResource
    {
        send(remaddr, bss, null, null, prio);
    }

    //
    // Utility
    //

    /**
     * Convenience method to get an {@link InetSocketAddress} from a {@link Socket}
     *
     * @param s socket from which to get the requested address
     * @param local true if you want the local address, false for remote
     * @return an {@link InetSocketAddress} object with the address
     */
    private static InetSocketAddress getaddr(Socket s, boolean local)
    {
        return (InetSocketAddress) (local ? s.getLocalSocketAddress() : s.getRemoteSocketAddress());
    }

    /**
     * Prints a connection string for a {@link Peer} object
     *
     * @param en map entry for the connection map
     * @return a string representing the connections to/from this remote peer
     */
    private static String getConnectionString(Map.Entry<InetSocketAddress, Peer> en)
    {
        assert en != null && en.getKey() != null && en.getValue() != null : ("invalid args");

        InetSocketAddress a = en.getKey();
        Peer p = en.getValue();

        StringBuilder csbld = new StringBuilder(128);

        csbld.append(a.toString());

        // input sockets

        csbld.append(": is ");

        if (p._is == null) {
            csbld.append("-");
        } else {
            csbld.append(printsock(p._is, true)).append("<-").append(printsock(p._is, false));
        }

        // output sockets

        csbld.append(" os ");

        if (p._os == null) {
            csbld.append("-");
        } else {
            csbld.append(printsock(p._os, true)).append("->").append(printsock(p._os, false));
        }

        return csbld.toString();
    }

    //
    // types
    //

    public static interface IConnection
    {
    }

    public static interface IConnectionManager
    {
        /**
         * @throws IOException to refuse connection
         */
        IReactor newIncomingConnection(IConnection c, InetSocketAddress from)
            throws IOException;

        /**
         * @return may be null if allowSend is false
         * @throws IOException to refuse connection
         */
        IConnector newOutgoingConnection(IConnection c, InetSocketAddress to,
                Object cookie) throws IOException;
    }

    // N.B. the methods don't require thread safety only when IConnectionManager
    // returns a unique instance on each newIncomingConnection call
    public static interface IReactor
    {
        // on each connection, the preamble will be sent only once, using
        // either IReactor's getPreamble() or IConnector's, depending on the
        // direction of connection borrowing. Therefore, the two methods should
        // return same contents for each connection.
        byte[][] getReactorPreamble_();

        // The method should process_ the received bytes in bs and return the
        // bytes to be sent back. return null if nothing to reply. Throw an
        // exception if the socket should be closed.
        //
        // The reactor will prefix the magic and the size to each reply message.
        //
        byte[][] react_(byte[] bs, int wirelen) throws Exception;

        void reactorDisconnected_();
    }

    // N.B. its methods don't require thread safety only if IConnectionManager
    // returns a unique instance on each newOutgoingConnection call
    public static interface IConnector
    {
        byte[][] getConnectorPreamble_();

        void connectorDisconnected_();
    }

    // access to mutable variables must be protected by sync(this)
    private class Peer implements IConnection
    {
        final BlockingPrioQueue<IEvent> _sendq =
            new BlockingPrioQueue<IEvent>(DaemonParam.TCP.QUEUE_LENGTH);
        final Socket _is;
        InetSocketAddress _key; // the key used to index peers in _map
        Socket _os;
        Thread _sendthd;
        volatile boolean _discarded; // this is to avoid duplicate discards.
        long _bytesrx;  // only accessible by the corresponding sending thread

        Peer(Socket is)
        {
            _is = is;
        }

        StrictLock getLock()
        {
            return _sendq.getLock();
        }
    }

    private static class EvSend implements IEvent
    {
        private final byte[][] _bss;
        final IResultWaiter _waiter;

        EvSend(byte[][] bs, IResultWaiter waiter)
        {
            _bss = bs;
            _waiter = waiter;
        }
    }

    //
    // members
    //

    private final int _magic;
    private final String _name;
    private final IConnectionManager _manager;
    private final ServerSocket _ss;
    private final boolean _sendable;
    private final int _maxRecvMsgSize;
    private volatile boolean _acceptPaused;
    private final Object _resumeAcceptSynchronizer = new Object();

    // socket map
    private final Map<InetSocketAddress, Peer> _map = new HashMap<InetSocketAddress, Peer>();

    // tx/rx counts

    private final AtomicLong _bytesrx = new AtomicLong();
    private final AtomicLong _bytestx = new AtomicLong();

    // socket disambiguator when we're creating multiple send/receive threads to the same peer

    private final AtomicLong socketcounter = new AtomicLong();

    // logging
    private static final Logger l = Util.l(TCPProactorMT.class);

    private static final EvSend EV_CLOSE = new EvSend(null, null);
}
