/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.net.ITransferStateListener.Key;
import com.aerofs.daemon.core.net.ITransferStateListener.Value;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;
import com.google.common.collect.Maps;

import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;

public class TransferState
        extends ConcurrentlyModifiableListeners<ITransferStateListener>
        implements IDumpStatMisc
{
    private final Map<Key, Value> _state = Maps.newHashMap();

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
            for (ITransferStateListener l : beginIterating_()) l.stateChanged_(key, value);
        } finally {
            endIterating_();
        }
    }

    public void ended_(SOCID socid, Endpoint ep, boolean failed)
    {
        Key key = new Key(socid, ep);
        Value value = new Value(failed);
        _state.remove(key);

        try {
            for (ITransferStateListener l : beginIterating_()) l.stateChanged_(key, value);
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