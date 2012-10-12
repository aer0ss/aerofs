package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.id.SOCID;

public interface IUploadStateListener {

    public static class Key {
        public final SOCID _socid;
        public final Endpoint _ep;

        Key(SOCID socid, Endpoint ep)
        {
            _socid = socid;
            _ep = ep;
        }

        @Override
        public int hashCode()
        {
            return _socid.hashCode() + _ep.hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o != null && _socid.equals(((Key) o)._socid) &&
                    _ep.equals(((Key) o)._ep));
        }
    }

    static public class Value {
        // @param done == total means completion, either failure or success
        public final long _done, _total;

        public Value(long done, long total)
        {
            _done = done;
            _total = total;
        }
    }

    void stateChanged_(Key key, Value value);
}
