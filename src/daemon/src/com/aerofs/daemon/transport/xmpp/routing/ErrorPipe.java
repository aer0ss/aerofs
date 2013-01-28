/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.routing;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.xmpp.IPipe;

import java.io.IOException;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.Set;

import static com.aerofs.daemon.core.net.Transports.TransportImplementation.NOOPTP;
import static com.aerofs.proto.Files.PBDumpStat;

/**
 * An implementation of {@link IPipe} that simply notifes the sender that there
 * was an error in sending a packet
 */
public class ErrorPipe implements IPipe
{
    public ErrorPipe(String id, int pref)
    {
        _bid = new BasicIdentifier(id, pref);
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
        throws Exception
    {
        // empty
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
        throws Exception
    {
        // empty
    }

    @Override
    public String id()
    {
        return _bid.id();
    }

    @Override
    public int rank()
    {
        return _bid.rank();
    }

    @Override
    public void init_() throws Exception
    {
        // empty
    }

    @Override
    public void start_()
    {
        // empty;
    }

    @Override
    public boolean ready()
    {
        return true;
    }

    @Override
    public void linkStateChanged_(Set<NetworkInterface> rem, Set<NetworkInterface> cur)
    {
        // empty;
    }

    @Override
    public void connect_(DID d)
    {
        // empty;
    }

    @Override
    public void disconnect_(DID did, Exception ex)
    {
        // empty;
    }

    @Override
    public Object send_(DID did, IResultWaiter wtr, Prio pri, byte[][] bss, Object cke)
        throws Exception
    {
        if (wtr != null) wtr.error(new IOException("invalid route"));
        return new Object(); // FIXME: ok...in this case what's a valid thing to return?
    }

    @Override
    public long getBytesRx(DID did)
    {
        return -1;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof ErrorPipe == false) return false;

        ErrorPipe er = (ErrorPipe)o;
        return _bid.equals(er._bid);
    }

    @Override
    public int hashCode()
    {
        return _bid.hashCode();
    }

    //
    // members
    //

    private final BasicIdentifier _bid;

    /**
     * Convenience static instance of {@link ErrorPipe}
     * FIXME (AG): this is broken because I've conflated the concept of pipes along with transports!
     * Basically, a pipe is an _internal_ construct to the xmpp routing mechanism, while transport
     * ids and rankings are at a different layer of abstraction entirely. This has no effect on
     * functionality, but it is broken from a design perspective.
     */
    public static final ErrorPipe ERROR_PIPE = new ErrorPipe(NOOPTP.id(), NOOPTP.rank());
}
