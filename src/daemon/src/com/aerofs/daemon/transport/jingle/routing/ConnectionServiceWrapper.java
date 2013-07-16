/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.daemon.transport.jingle.routing;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.IScheduler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.aerofs.proto.Files.PBDumpStat;
import static com.google.common.base.Preconditions.checkArgument;

public class ConnectionServiceWrapper implements IPipeDebug
{
    private static final Logger l = Loggers.getLogger(ConnectionServiceWrapper.class);

    private final IScheduler scheduler;
    private final ISignalledConnectionService connectionService;
    private final Map<DID, DIDPipeRouter<? extends IConnectionService>> peers = new HashMap<DID, DIDPipeRouter<? extends IConnectionService>>();

    public ConnectionServiceWrapper(IScheduler scheduler, ISignalledConnectionService connectionService)
    {
        this.scheduler = scheduler;
        this.connectionService = connectionService;
    }

    public void init_() throws Exception
    {
        connectionService.init();
    }

    public void start_()
    {
        connectionService.start();
    }

    public boolean ready()
    {
        return connectionService.ready();
    }

    public void linkStateChanged_(Set<NetworkInterface> rem, Set<NetworkInterface> cur)
        throws ExNoResource
    {
        connectionService.linkStateChanged(rem, cur);
    }

    public void xmppServerConnected_()
        throws ExNoResource
    {
        connectionService.signallingServiceConnected();
    }

    public void xmppServerDisconnected_()
        throws ExNoResource
    {
        connectionService.signallingServiceDisconnected();
    }

    public void deviceConnected_(DID did, IConnectionService connectionService)
    {
        l.info("+d:{}", did);

        // it is possible for peers to connect without a packet being sent from
        // us first

        DIDPipeRouter<? extends IConnectionService> dpr = getOrCreate(did);
        dpr.deviceConnected_(connectionService);
    }

    public void deviceDisconnected_(DID did, IConnectionService connectionService)
    {
        l.info("-d:{}", did);

        DIDPipeRouter<? extends IConnectionService> dpr = peers.get(did);

        // this can occur because underlying layers sometimes signal
        // disconnection, regardless of whether a valid connection takes
        // place or not

        // FIXME (AG): make underlying layers more strict about when a disconnect is signalled

        if (dpr == null) {
            l.warn("disconnect before connect d:{}", did);
            return;
        }

        dpr.deviceDisconnected_(connectionService);

        // IMPORTANT - don't remove the dpr! FIXME: find a way to purge dprs
    }

    public void disconnect_(DID did, Exception ex)
        throws ExNoResource
    {
        checkArgument(did != null && ex != null, "null did or ex");
        connectionService.disconnect(did, ex);
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
        try {
            connectionService.dumpStat(template, bd);
        } catch (Exception e) {
            l.warn("fail dumpstat err:", e);
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        try {
            connectionService.dumpStatMisc(indent, indentUnit, ps);
        } catch (Exception e) {
            l.warn("fail dumpstatmisc err:", e);
        }
    }

    @Override
    public long getBytesReceived(DID did)
    {
        return connectionService.getBytesReceived(did);
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
        if (!peers.containsKey(did)) {
            peers.put(did, new DIDPipeRouter<ISignalledConnectionService>(did, scheduler, connectionService));
        }

        return peers.get(did);
    }
}
