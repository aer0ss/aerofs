package com.aerofs.daemon.core.net;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.net.IUploadStateListener.Key;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;

// see IDownloadListener for valid state transitions

public class UploadState extends ConcurrentlyModifiableListeners<IUploadStateListener>
        implements IDumpStatMisc
{

    private final Map<Key, Value> _state = new HashMap<Key, Value>();

    public Map<Key, Value> getStates_()
    {
        return _state;
    }

    // @param done == total means completion, either failure or success
    public void progress_(SOCID socid, Endpoint ep, long done, long total)
    {
        Key key = new Key(socid, ep);
        Value value = new Value(done, total);
        if (done != total) {
            _state.put(key, value);
        } else {
            _state.remove(key);
        }

        try {
            for (IUploadStateListener l : beginIterating_()) l.stateChanged_(key, value);
        } finally {
            endIterating_();
        }
    }

    @Override
    public void dumpStatMisc(String indent2, String indentUnit, PrintStream ps)
    {
        for (Entry<Key, Value> en : getStates_().entrySet()) {
            long done = en.getValue()._done;
            long total = en.getValue()._total;
            long percent = total == 0 ? 0 : (done * 100 / total);
            ps.println(indent2 + en.getKey()._socid + " -> " + en.getKey()._ep +
                    ' ' + String.format(".%1$02d ", percent) + done + '/' + total);
        }
    }
}
