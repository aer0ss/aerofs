/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.id.SOCID;

/**
 * Implement this listener to be notified whenever an ongoing transfer is updated
 * (either it completes, more data is transferred, etc.)
 */
public interface ITransferStateListener
{
    //
    // interface methods
    //

    void onTransferStateChanged_(TransferredItem item, TransferProgress progress);

    //
    // types
    //

    public static final class TransferredItem
    {
        public final SOCID _socid;
        public final Endpoint _ep;

        /**
         * (AT) made public to help with testing. Also, since this class is used as a struct,
         *   there's no good reason why the constructor should be anything but public.
         */
        public TransferredItem(SOCID socid, Endpoint ep)
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
            return this == o || (o != null && _socid.equals(((TransferredItem) o)._socid) && _ep.equals(((TransferredItem) o)._ep));
        }

        @Override
        public String toString()
        {
            return _socid.toString() + '@' + _ep.toString();
        }
    }

    public static final class TransferProgress
    {
        /**
         * the value of the fields is to be interpretted as follows:
         *   if (_done == _total) // then the transfer has terminated either in success or in failure
         *     if (_failed)
         *       ; // the transfer has failed
         *     else
         *       ; // the transfer has completed in success
         *   else
         *     ; // ignore _failed and the current progress is _done / _total
         */
        public final boolean _failed;
        public final long _done;
        public final long _total;

        public TransferProgress(long done, long total)
        {
            _done = done;
            _total = total;
            _failed = false;
        }

        public TransferProgress(boolean failed)
        {
            _done = _total = 0;
            _failed = failed;
        }
    }
}
