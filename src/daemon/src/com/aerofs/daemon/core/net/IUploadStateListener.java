package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.id.SOCKID;

public interface IUploadStateListener {

    public static class Key {
        public final SOCKID _k;
        public final Endpoint _ep;

        Key(SOCKID k, Endpoint ep)
        {
            _k = k;
            _ep = ep;
        }

        @Override
        public int hashCode()
        {
            return _k.hashCode() + _ep.hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o != null && _k.equals(((Key) o)._k) &&
                    _ep.equals(((Key) o)._ep));
        }
    }

    static public class Value {
        // @param done == total means completion, either failure or success
        public final long _done, _total;

        Value(long done, long total)
        {
            _done = done;
            _total = total;
        }
    }

    void stateChanged_(Key key, Value value);
}
