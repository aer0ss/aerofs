/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.daemon.transport.xmpp.routing;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.daemon.transport.xmpp.IPipe;
import com.aerofs.daemon.transport.xmpp.ISignalledPipe;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoResource;
import com.google.common.collect.ImmutableSet;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.aerofs.proto.Files.PBDumpStat;

/**
 * FIXME: should I parameterize SignalledPipeFanout and have XMPP use ISignalledPipe?
 *
 */
public class SignalledPipeFanout implements IPipeDebug
{
    public SignalledPipeFanout(IScheduler sched, Set<ISignalledPipe> pipes)
    {
        assert sched != null && pipes != null && pipes.size() > 0 : ("invalid args");

        _pream = "spf:";
        _sched = sched;
        _pipes = ImmutableSet.copyOf(pipes);
    }

    public void init_() throws Exception
    {
        for (IPipe p : _pipes) {
            l.info(_pream + " init p:" + p.id());
            p.init_();
        }

        l.info(_pream + " inited");
    }

    public void start_()
    {
        for (IPipe p : _pipes) {
            l.info(_pream + " start p:" + p.id());
            p.start_();
        }

        l.info(_pream + " started");
    }

    public boolean ready()
    {
        boolean oneready = false;

        for (IPipe p : _pipes) {
            if (p.ready()) {
                l.info(_pream + " p:" + p.id() + " ready");
                oneready = true;
                break;
            }
        }

        l.info(_pream + " ready:" + oneready);

        return oneready;
    }

    public void linkStateChanged_(Set<NetworkInterface> rem, Set<NetworkInterface> cur)
        throws ExNoResource
    {
        for (IPipe p : _pipes) {
            l.info(_pream + " lsc p:" + p.id());
            p.linkStateChanged_(rem, cur);
        }
    }

    public void xmppServerConnected_()
        throws ExNoResource
    {
        for (ISignalledPipe p : _pipes) {
            l.info(_pream + " xmpp server connected p:" + p.id());
            p.signallingChannelConnected_();
        }
    }

    public void xmppServerDisconnected_()
        throws ExNoResource
    {
        for (ISignalledPipe p : _pipes) {
            l.info(_pream + " xmpp server disconnected p:" + p.id());
            p.signallingChannelDisconnected_();
        }
    }

    public void peerConnected_(DID did, IPipe p)
    {
        l.info(_pream + " p:" + p.id() + " +d:" + did);

        // it is possible for peers to connect without a packet being sent from
        // us first

        DIDPipeRouter<? extends IPipe> dpr = getOrCreate(did);
        dpr.peerConnected_(p);
    }

    public void peerDisconnected_(DID did, IPipe p)
    {
        l.info(_pream + " p:" + p.id() + " -d:" + did);

        DIDPipeRouter<? extends IPipe> dpr = _peers.get(did);

        // this occurs because underlying layers will always signal
        // disconnection, regardless of whether a valid connection takes
        // place or not

        // FIXME: make underlying layers more strict about when a disconnect is signalled

        if (dpr == null) {

            l.warn(_pream + " disconnect before connect p:" + p.id() + " d:" + did);
            return;
        }

        dpr.peerDisconnected_(p);

        // IMPORTANT - don't remove the dpr! FIXME: find a way to purge dprs
    }

    public void disconnect_(DID did, Exception ex)
        throws ExNoResource
    {
        assert did != null && ex != null : (_pream + " null did or ex");

        for (IPipe p : _pipes) {
            l.info(_pream + " disconnect p:" + p.id() + " d:" + did);
            p.disconnect_(did, ex);
        }
    }

    public Object send_(DID did, @Nullable IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cke)
        throws Exception
    {
        DIDPipeRouter<? extends IPipe> dpr = getOrCreate(did);
        return dpr.send_(wtr, pri, bss, cke);
    }

    //
    // IPipeDebug methods
    //

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
    {
        for(IPipe p : _pipes) {
            try {
                l.info(_pream + " dumpstat p:" + p.id());
                p.dumpStat(template, bd);
            } catch (Exception e) {
                l.warn(_pream + " cannot dumpstat p:" + p.id() + "err:" + e);
            }
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        String indentmore = indent + indentUnit;

        for(IPipe p : _pipes) {
            try {
                l.info(_pream + " dumpstatmisc p:" + p.id());
                ps.println(indent + "ucast:" + p.id());
                p.dumpStatMisc(indentmore, indentUnit, ps);
            } catch (Exception e) {
                l.warn(_pream + " cannot dumpstatmisc p:" + p.id() + "err:" + e);
            }
        }
    }

    @Override
    public long getBytesRx(DID did)
    {
        int total = 0;
        for (ISignalledPipe p : _pipes) {
            l.info(_pream + " bytesrx p:" + p.id());
            total += p.getBytesRx(did);
        }

        return total;
    }

    //
    // utility methods
    //

    /**
     * Gets the {@link DIDPipeRouter} instance for a peer, or creates one if it
     * does not exist
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer for which the <code>DIDPipeRouter</code>
     * should be retrieved or created
     * @return a valid <code>DIDPipeRouter</code>
     */
    DIDPipeRouter<? extends IPipe> getOrCreate(DID did)
    {
        if (!_peers.containsKey(did)) {
            _peers.put(did, new DIDPipeRouter<ISignalledPipe>(did, _sched, _pipes));
        }

        return _peers.get(did);
    }

    //
    // members
    //

    private final String _pream;
    private final IScheduler _sched;
    private final ImmutableSet<ISignalledPipe> _pipes;
    private final Map<DID, DIDPipeRouter<? extends IPipe>> _peers = new HashMap<DID, DIDPipeRouter<? extends IPipe>>();

    private static final Logger l = Util.l(SignalledPipeFanout.class);
}
