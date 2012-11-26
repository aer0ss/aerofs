/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.id.SOCID;

/* valid state transitions for a given socid:
 *
 *  o -> enqueued
 *  enqueued -> started, ended
 *  started -> ongoing, ended, enqueued
 *  ongoing -> ongoing, ended, enqueued
 *  ended -> enqueued
 */

public interface IDownloadStateListener {

    public static abstract class State {
    }

    // cannot use this.getClass().getClassName() for toString() due to
    // obfuscation
    public static class Enqueued extends State {
        private Enqueued() {}
        @Override
        public String toString()
        {
            return "Enqueued";
        }
        static final Enqueued SINGLETON = new Enqueued();
    }

    public static class Started extends State {
        private Started() {}
        @Override
        public String toString()
        {
            return "Started";
        }
        public static final Started SINGLETON = new Started();
    }

    public static class Ended extends State {
        private Ended(boolean okay)
        {
            _okay = okay;
        }
        public final boolean _okay;
        @Override
        public String toString()
        {
            return "Ended " + (_okay ? "okay" : "failed");
        }
        public static final Ended SINGLETON_OKAY = new Ended(true);
        public static final Ended SINGLETON_FAILED = new Ended(false);
    }

    public static class Ongoing extends State {
        public Ongoing(Endpoint ep, long done, long total)
        {
            _ep = ep;
            _done = done;
            _total = total;
        }
        public final Endpoint _ep;
        public final long _done, _total;
        @Override
        public String toString()
        {
            long percent = _total == 0 ? 0 : (_done * 100 / _total);
            return "Ongoing <- " + _ep + ' ' + String.format(".%1$02d ", percent)
                + _done + '/' + _total;
        }
    }

    void stateChanged_(SOCID socid, State newState);
}
