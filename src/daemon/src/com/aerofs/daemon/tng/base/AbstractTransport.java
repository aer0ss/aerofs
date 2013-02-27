/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.lib.Util;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Files.PBDumpStat;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;

import java.io.PrintStream;

public abstract class AbstractTransport implements ITransport
{
    protected final Logger l = Util.l(getClass());

    private final String _id;
    private final Preference _pref;
    private final IEventLoop _executor;
    private final IPresenceService _presenceService; // FIXME: remove presence service!
    private final IMaxcastService _maxcastService;
    private final IUnicastService _unicastService;

    protected AbstractTransport(String id, Preference pref, IEventLoop executor,
            IPresenceService presenceService, IUnicastService unicastService,
            IMaxcastService maxcastService)
    {
        this._id = id;
        this._pref = pref;
        this._executor = executor;
        this._presenceService = presenceService;
        this._unicastService = unicastService;
        this._maxcastService = maxcastService;
    }

    @Override
    public void start_()
    {
        _executor.start_();
        _presenceService.start_();
        _unicastService.start_();
        _maxcastService.start_();
    }

    @Override
    public String id()
    {
        return _id;
    }

    @Override
    public Preference pref()
    {
        return _pref;
    }

    @Override
    public int hashCode()
    {
        return pref().hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (!(o instanceof AbstractTransport)) return false;

        AbstractTransport transport = (AbstractTransport) o;
        return transport.pref() == pref();
    }

    @Override
    public String toString()
    {
        return id();
    }

    //
    // IMPORTANT: I thought about catching Throwable, but I think it's better to let it bubble up and crash the server
    // IMPORTANT: Never ignore returns from methods
    // FIXME: auto-generate all the below "executeInTransportExecutor" calls
    //

    /**
     * Enqueue a method for processing by the <code>XMPPBasedTransportFactory</code> {@link
     * EventDispatcher}. The thread calling this method will <em>block</em> until the event can be
     * enqueued. The event is run from within the event-dispatch thread and can safely use the
     * non-thread-safe methods
     *
     * @param pri {@link Prio} priority of the event
     */
    private void executeInTransportExecutor(Runnable runnable, Prio pri)
    {
        assertNonEventThread();
        _executor.execute(runnable, pri);
    }

    private void assertNonEventThread()
    {
        //_executor.assertNonEventThread();
    }

    @Override
    public ListenableFuture<Void> sendDatagram_(final DID did, final SID sid, final byte[] payload,
            final Prio pri)
    {
        assertNonEventThread();

        final UncancellableFuture<Void> returned = UncancellableFuture.create();

        executeInTransportExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("send ucast pkt d:" + did);
                returned.chain(_unicastService.sendDatagram_(did, sid, payload, pri));
            }
        }, pri);

        return returned;
    }

    @Override
    public ListenableFuture<IOutgoingStream> beginStream_(final StreamID id, final DID did,
            final SID sid, final Prio pri)
    {
        assertNonEventThread();

        final UncancellableFuture<IOutgoingStream> returned = UncancellableFuture.create();

        executeInTransportExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("begin out stream id:" + id + " d:" + did + " s:" + sid);
                returned.chain(_unicastService.beginStream_(id, did, sid, pri));
            }
        }, pri);

        return returned;
    }

    @Override
    public ListenableFuture<Void> pulse_(final DID did, final Prio pri)
    {
        assertNonEventThread();

        final UncancellableFuture<Void> returned = UncancellableFuture.create();

        executeInTransportExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("start pulse d:" + did);
                returned.chain(_unicastService.pulse_(did, pri));
            }
        }, pri);

        return returned;
    }

    @Override
    public ListenableFuture<Void> sendDatagram_(final int maxcastId, final SID sid,
            final byte[] payload, final Prio pri)
    {
        assertNonEventThread();

        final UncancellableFuture<Void> returned = UncancellableFuture.create();

        executeInTransportExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("send mcast pkt s:" + sid + "maxcastid:" + maxcastId);
                returned.chain(_maxcastService.sendDatagram_(maxcastId, sid, payload, pri));
            }
        }, pri);

        return returned;
    }

    @Override
    public ListenableFuture<ImmutableSet<DID>> getMaxcastUnreachableOnlineDevices_()
    {
        assertNonEventThread();

        final UncancellableFuture<ImmutableSet<DID>> returned = UncancellableFuture.create();

        executeInTransportExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("get muod");
                returned.chain(_maxcastService.getMaxcastUnreachableOnlineDevices_());
            }
        }, Prio.LO);

        return returned;
    }

    @Override
    public ListenableFuture<Void> updateLocalStoreInterest_(final ImmutableSet<SID> added,
            final ImmutableSet<SID> removed)
    {
        assertNonEventThread();

        final UncancellableFuture<Void> returned = UncancellableFuture.create();

        executeInTransportExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                l.info("update store interest");
                returned.chain(_maxcastService.updateLocalStoreInterest_(added, removed));
            }
        }, Prio.LO);

        return returned;
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder builder) // FIXME: iterate
    {
        try {
            _presenceService.dumpStat(template, builder);
        } catch (Exception e) {
            l.warn("fail dumpstat ps");
        }

        try {
            _unicastService.dumpStat(template, builder);
        } catch (Exception e) {
            l.warn("fail dumpstat uc");
        }

        try {
            _maxcastService.dumpStat(template, builder);
        } catch (Exception e) {
            l.warn("fail dumpstat mc");
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps) // FIXME: iterate
    {
        String indentChild = indent + indentUnit;

        ps.println(indent + "eq");
        try {
            _executor.dumpStatMisc(indentChild, indentUnit, ps);
        } catch (Exception e) {
            ps.println("fail dumpstatmisc eq");
        }

        ps.println(indent + "ps");
        try {
            _presenceService.dumpStatMisc(indentChild, indentUnit, ps);
        } catch (Exception e) {
            ps.println("fail dumpstatmisc ps");
        }

        ps.println(indent + "uc");
        try {
            _unicastService.dumpStatMisc(indentChild, indentUnit, ps);
        } catch (Exception e) {
            ps.println("fail dumpstatmisc uc");
        }

        ps.println(indent + "mc");
        try {
            _maxcastService.dumpStatMisc(indentChild, indentUnit, ps);
        } catch (Exception e) {
            ps.println("fail dumpstatmisc mc");
        }
    }
}
