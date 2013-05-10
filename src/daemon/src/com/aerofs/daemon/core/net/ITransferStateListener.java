/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.id.SOCID;

public interface ITransferStateListener
{
    public static class Key {
        public final SOCID _socid;
        public final Endpoint _ep;

        /**
         * (AT) made public to help with testing. Also, since this class is used as a struct,
         *   there's no good reason why the constructor should be anything but public.
         */
        public Key(SOCID socid, Endpoint ep)
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

        @Override
        public String toString()
        {
            return _socid.toString() + '@' + _ep.toString();
        }
    }

    static public class Value {
        // @param done == total means completion, either failure or success
        public final boolean _failed;
        public final long _done, _total;

        public Value(long done, long total)
        {
            _done = done;
            _total = total;
            _failed = false;
        }

        public Value(long done, long total, boolean failed)
        {
            _done = done;
            _total = total;
            _failed = failed;
        }

        public Value(boolean failed)
        {
            _done = _total = 0;
            _failed = failed;
        }
    }

    void stateChanged_(Key key, Value value);
}
