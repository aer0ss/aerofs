/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.daemon.transport.xmpp.routing;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.xmpp.IConnectionService;
import com.aerofs.daemon.transport.xmpp.ISignalledConnectionService;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.base.ex.ExNoResource;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.aerofs.proto.Files.PBDumpStat;

/**
 * FIXME: should I parameterize SignalledPipeFanout and have XMPP use ISignalledConnectionService?
 *
 */
public class SignalledPipeFanout implements IPipeDebug
{
    public SignalledPipeFanout(IScheduler sched, Set<ISignalledConnectionService> pipes)
    {
        assert sched != null && pipes != null && pipes.size() > 0 : ("invalid args");

        _pream = "spf:";
        _sched = sched;
        _pipes = ImmutableSet.copyOf(pipes);
    }

    public void init_() throws Exception
    {
        for (IConnectionService p : _pipes) {
            l.info(_pream + " init p:" + p.id());
            p.init();
        }

        l.info(_pream + " inited");
    }

    public void start_()
    {
        for (IConnectionService p : _pipes) {
            l.info(_pream + " start p:" + p.id());
            p.start();
        }

        l.info(_pream + " started");
    }

    public boolean ready()
    {
        boolean oneready = false;

        for (IConnectionService p : _pipes) {
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
        for (IConnectionService p : _pipes) {
            l.info(_pream + " lsc p:" + p.id());
            p.linkStateChanged(rem, cur);
        }
    }

    public void xmppServerConnected_()
        throws ExNoResource
    {
        for (ISignalledConnectionService p : _pipes) {
            l.info(_pream + " xmpp server connected p:" + p.id());
            p.signallingServiceConnected();
        }
    }

    public void xmppServerDisconnected_()
        throws ExNoResource
    {
        for (ISignalledConnectionService p : _pipes) {
            l.info(_pream + " xmpp server disconnected p:" + p.id());
            p.signallingServiceDisconnected();
        }
    }

    public void deviceConnected_(DID did, IConnectionService p)
    {
        l.info(_pream + " p:" + p.id() + " +d:" + did);

        // it is possible for peers to connect without a packet being sent from
        // us first

        DIDPipeRouter<? extends IConnectionService> dpr = getOrCreate(did);
        dpr.deviceConnected_(p);
    }

    public void deviceDisconnected_(DID did, IConnectionService p)
    {
        l.info(_pream + " p:" + p.id() + " -d:" + did);

        DIDPipeRouter<? extends IConnectionService> dpr = _peers.get(did);

        // this occurs because underlying layers will always signal
        // disconnection, regardless of whether a valid connection takes
        // place or not

        // FIXME: make underlying layers more strict about when a disconnect is signalled

        if (dpr == null) {

            l.warn(_pream + " disconnect before connect p:" + p.id() + " d:" + did);
            return;
        }

        dpr.deviceDisconnected_(p);

        // IMPORTANT - don't remove the dpr! FIXME: find a way to purge dprs
    }

    public void disconnect_(DID did, Exception ex)
        throws ExNoResource
    {
        assert did != null && ex != null : (_pream + " null did or ex");

        for (IConnectionService p : _pipes) {
            l.info(_pream + " disconnect p:" + p.id() + " d:" + did);
            p.disconnect(did, ex);
        }
    }

    public Object send_(DID did, @Nullable IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cke)
        throws Exception
    {
        DIDPipeRouter<? extends IConnectionService> dpr = getOrCreate(did);
        return dpr.send_(wtr, pri, bss, cke);
    }

    //
    // IPipeDebug methods
    //

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
    {
        for(IConnectionService p : _pipes) {
            try {
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

        for(IConnectionService p : _pipes) {
            try {
                ps.println(indent + "ucast:" + p.id());
                p.dumpStatMisc(indentmore, indentUnit, ps);
            } catch (Exception e) {
                l.warn(_pream + " cannot dumpstatmisc p:" + p.id() + "err:" + e);
            }
        }
    }

    @Override
    public long getBytesReceived(DID did)
    {
        int total = 0;
        for (ISignalledConnectionService p : _pipes) {
            l.info(_pream + " bytesrx p:" + p.id());
            total += p.getBytesReceived(did);
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
    DIDPipeRouter<? extends IConnectionService> getOrCreate(DID did)
    {
        if (!_peers.containsKey(did)) {
            _peers.put(did, new DIDPipeRouter<ISignalledConnectionService>(did, _sched, _pipes));
        }

        return _peers.get(did);
    }

    //
    // members
    //

    private final String _pream;
    private final IScheduler _sched;
    private final ImmutableSet<ISignalledConnectionService> _pipes;
    private final Map<DID, DIDPipeRouter<? extends IConnectionService>> _peers = new HashMap<DID, DIDPipeRouter<? extends IConnectionService>>();

    private static final Logger l = Loggers.getLogger(SignalledPipeFanout.class);
}
