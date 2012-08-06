package com.aerofs.daemon.core.net;

import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.aerofs.daemon.core.net.IDownloadStateListener.*;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.notifier.Listeners;

// see IDownloadStateListener for valid state transitions

public class DownloadState extends Listeners<IDownloadStateListener>
implements IDumpStatMisc {

    private final Map<SOCKID, State> _states = new TreeMap<SOCKID, State>();

    public Map<SOCKID, State> getStates_()
    {
        return _states;
    }

    public void enqueued_(SOCKID k)
    {
        //if (k.cid().isMeta()) return;

        State newState = Enqueued.SINGLETON;
        State oldState = _states.put(k, newState);
        assert oldState == null || oldState instanceof Started || oldState instanceof Ongoing :
                k + " old state: " + oldState;
        notifyListeners_(k, newState);
    }

    public void started_(SOCKID k)
    {
        //if (k.cid().isMeta()) return;

        State newState = Started.SINGLETON;
        State oldState = _states.put(k, newState);
        assert oldState instanceof Enqueued;
        notifyListeners_(k, newState);
    }

    public void ongoing_(SOCKID k, Endpoint ep, long done, long total)
    {
        //if (k.cid().isMeta()) return;

        State newState = new Ongoing(ep, done, total);
        State oldState = _states.put(k, newState);
        assert oldState instanceof Started || oldState instanceof Ongoing;
        notifyListeners_(k, newState);
    }

    public void ended_(SOCKID k, boolean okay)
    {
        //if (k.cid().isMeta()) return;

        State oldState = _states.remove(k);
        assert oldState instanceof Started || oldState instanceof Ongoing ||
            oldState instanceof Enqueued;

        notifyListeners_(k, okay ? Ended.SINGLETON_OKAY : Ended.SINGLETON_FAILED);
    }

    private void notifyListeners_(SOCKID k, State newState)
    {
        try {
            for (IDownloadStateListener l : beginIterating_()) {
                l.stateChanged_(k, newState);
            }
        } finally {
            endIterating_();
        }
    }

    @Override
    public void dumpStatMisc(String indent2, String indentUnit, PrintStream ps)
    {
        for (Entry<SOCKID, State> en : getStates_().entrySet()) {
            State s = en.getValue();
            ps.println(indent2 + en.getKey() + " " + s);
        }
    }
}
