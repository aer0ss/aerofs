/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.net.tng;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.base.id.SID;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * The presence is *edge* triggered
 * <p/>
 * must use enqueueBlocking() rather than enqueue() to file the event. we need reliable receiving of
 * these events
 */
public class EIPresence implements IEvent
{
    public final Map<DID, Collection<SID>> _did2sids;
    public final ITransport _tp;
    public final boolean _online;

    /**
     * @param did2sids must be immutable. online == false and did2sids == null indicates that all
     * the devices are offline. did2sids mustn't be null if online == true.
     */
    public EIPresence(ITransport tp, boolean online, Map<DID, Collection<SID>> did2sids)
    {
        _did2sids = did2sids;
        _tp = tp;
        _online = online;
    }

    public EIPresence(ITransport tp, boolean online, DID did, Collection<SID> sids)
    {
        this(tp, online, Collections.singletonMap(did, sids));
    }

    public EIPresence(ITransport tp, boolean online, DID did, SID sid)
    {
        this(tp, online, did, Collections.singleton(sid));
    }
}
