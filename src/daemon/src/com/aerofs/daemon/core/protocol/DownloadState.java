/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.protocol.IDownloadStateListener.Ended;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Enqueued;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Ongoing;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.Started;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.State;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;
import com.google.common.collect.Maps;

// see IDownloadStateListener for valid state transitions

public class DownloadState extends ConcurrentlyModifiableListeners<IDownloadStateListener>
implements IDumpStatMisc {

    private final Map<SOCID, State> _states = Maps.newTreeMap();

    public Map<SOCID, State> getStates_()
    {
        return _states;
    }

    public void enqueued_(SOCID socid)
    {
        //if (socid.cid().isMeta()) return;

        State newState = Enqueued.SINGLETON;
        State oldState = _states.put(socid, newState);
        assert oldState == null || oldState instanceof Started || oldState instanceof Ongoing :
                socid + " old state: " + oldState;
        notifyListeners_(socid, newState);
    }

    public void started_(SOCID socid)
    {
        //if (socid.cid().isMeta()) return;

        State newState = Started.SINGLETON;
        State oldState = _states.put(socid, newState);
        assert oldState instanceof Enqueued;
        notifyListeners_(socid, newState);
    }

    public void ongoing_(SOCID socid, Endpoint ep, long done, long total)
    {
        //if (socid.cid().isMeta()) return;

        State newState = new Ongoing(ep, done, total);
        State oldState = _states.put(socid, newState);
        assert oldState instanceof Started || oldState instanceof Ongoing;
        notifyListeners_(socid, newState);
    }

    public void ended_(SOCID socid, boolean okay)
    {
        //if (socid.cid().isMeta()) return;

        State oldState = _states.remove(socid);
        assert oldState instanceof Started || oldState instanceof Ongoing ||
            oldState instanceof Enqueued;

        notifyListeners_(socid, okay ? Ended.SINGLETON_OKAY : Ended.SINGLETON_FAILED);
    }

    private void notifyListeners_(SOCID socid, State newState)
    {
        try {
            for (IDownloadStateListener l : beginIterating_()) {
                l.stateChanged_(socid, newState);
            }
        } finally {
            endIterating_();
        }
    }

    @Override
    public void dumpStatMisc(String indent2, String indentUnit, PrintStream ps)
    {
        for (Entry<SOCID, State> en : getStates_().entrySet()) {
            State s = en.getValue();
            ps.println(indent2 + en.getKey() + " " + s);
        }
    }
}
