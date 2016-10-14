package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Lazy;
import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.defects.Defect;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.slf4j.Logger;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.aerofs.defects.Defects.newFrequentDefect;
import static com.google.common.base.Preconditions.checkState;

/**
 * Manages scan sessions on a per-root basis
 */
public class ScanSessionQueue implements IDumpStatMisc
{
    private static Logger l = Loggers.getLogger(ScanSessionQueue.class);
    private static Lazy<Defect> defect = new Lazy<>(() -> newFrequentDefect("core.scan_session_queue"));

    private static class TimeKey implements Comparable<TimeKey>
    {
        final long _time;   // the absolute time at which the entry should run
        final long _delay;  // the relative delay before which the entry should run
        final int _seq;     // the sequence number to make sure the entries with the same
                            // timestamp are FIFO ordered

        private static int s_seq;

        TimeKey(long time, long delay)
        {
            _time = time;
            _delay = delay;
            _seq = s_seq++;
        }

        @Override
        public int compareTo(TimeKey timeKey)
        {
            int comp = BaseUtil.compare(_time, timeKey._time);
            return comp == 0 ? BaseUtil.compare(_seq, timeKey._seq) : comp;
        }

        @Override
        public String toString()
        {
            return "time " + _time + " seq " + _seq + " delay " + _delay;
        }
    }

    private static class PathKey
    {
        final Set<String> _absPaths;
        final boolean _recursive;

        PathKey(Set<String> absPaths, boolean recursive)
        {
            _absPaths = absPaths;
            _recursive = recursive;
        }

        @Override
        public int hashCode()
        {
            return _absPaths.hashCode() + (_recursive ? 1 : 0);
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o != null && _absPaths.equals(((PathKey) o)._absPaths) &&
                    _recursive == ((PathKey) o)._recursive);
        }

        @Override
        public String toString()
        {
            return _absPaths.stream().collect(Collectors.joining(",", "[", "]")) + ":" + _recursive;
        }
    }

    public static class Factory
    {
        private final CoreScheduler _sched;
        private final ScanSession.Factory _factSS;
        private final TimeSource _timeSource;

        @Inject
        public Factory(CoreScheduler sched, ScanSession.Factory factSS, TimeSource timeSource)
        {
            _sched = sched;
            _factSS = factSS;
            _timeSource = timeSource;
        }

        public ScanSessionQueue create_(LinkerRoot root)
        {
            return new ScanSessionQueue(this, root);
        }
    }

    private final Factory _f;

    private final LinkerRoot _root;

    private final SortedMap<TimeKey, PathKey> _time2path = Maps.newTreeMap();
    private final Map<PathKey, TimeKey> _path2time = Maps.newHashMap();

    // the starting time for the next scan session
    private long _nextSchedule = Long.MAX_VALUE;

    private int _scheduleSeq;

    // whether there is an ongoing scan session
    private boolean _ongoing;
    // callbacks to be fired when the current session ends
    private List<ScanCompletionCallback> _callbacks = Lists.newArrayList();

    ScanSessionQueue(Factory f, LinkerRoot root)
    {
        _f = f;
        _root = root;
    }

    public SID sid()
    {
        return _root.sid();
    }

    public String absRootanchor()
    {
        return _root.absRootAnchor();
    }

    public void recursiveScanImmediately_(Set<String> absPaths,
            @Nonnull ScanCompletionCallback callback)
    {
        scanImpl_(new PathKey(absPaths, true), 0, callback);
    }

    public void scheduleScanImmediately(Set<String> absPaths, boolean recursive) {
        _f._sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                scanImmediately_(absPaths, recursive);
            }
        });
    }

    public void scanImmediately_(Set<String> absPaths, boolean recursive)
    {
        scanImpl_(new PathKey(absPaths, recursive), 0, () -> {});
    }

    public void scanAfterDelay_(Set<String> absPaths, boolean recursive)
    {
        scanImpl_(new PathKey(absPaths, recursive), LibParam.EXP_RETRY_MIN_DEFAULT, () -> {});
    }

    /**
     * @param delay relative delay before running the requested scan
     *
     * N.B this method is available only to core threads
     */
    private void scanImpl_(PathKey pk, long delay, @Nonnull ScanCompletionCallback callback)
    {
        assert delay >= 0;
        long time = enqueue_(pk, delay);
        schedule_(delay, time, callback);
    }

    /**
     * Add the request to the queue. Replace the existing entry if the new request should happen
     * sooner than scheduled.
     */
    private long enqueue_(PathKey pk, long millisecondDelay)
    {
        assert millisecondDelay >= 0;

        // TODO (WW) replace the new request with an existing one if the latter covers the former,
        // e.g. if they have identical path but the new request is not recursive and the existing
        // one is.

        TimeKey tkOld = _path2time.get(pk);
        long time = _f._timeSource.getTime() + millisecondDelay;

        boolean replace = tkOld != null && tkOld._time > time;
        if (replace) {
            // The value removed from _path2time must be the tkOld itself, whereas the value removed
            // from _time2path can be (and should be) a different but equivalent object.
            Util.verify(_path2time.remove(pk) == tkOld);
            Util.verify(_time2path.remove(tkOld).equals(pk));
        }

        if (replace || tkOld == null) {
            l.warn("enq {} in {} replace {}", pk, millisecondDelay, replace);
            TimeKey tk = new TimeKey(time, millisecondDelay);
            TimeKey ptk = _path2time.put(pk, tk);
            checkState(ptk == null, "" + ptk);
            PathKey ppk = _time2path.put(tk, pk);
            checkState(ppk == null, "" + ppk);
        }

        return time;
    }

    private void schedule_(long delay, long time, final ScanCompletionCallback callback)
    {
        assert delay >= 0;

        _callbacks.add(callback);
        schedule_(delay, time);
    }

    /**
     * Schedule an event that runs the next scan session. No-op if the next session has been
     * scheduled.
     *
     * @param delay relative timeout
     * @param time absolute timeout
     */
    private void schedule_(long delay, long time)
    {
        assert delay >= 0;

        if (_ongoing || _nextSchedule <= time) {
            l.info("skip schedule {} {} {} {}", delay, time, _ongoing, _nextSchedule);
            return;
        }

        _nextSchedule = time;
        final int seq = ++_scheduleSeq;
        AbstractEBSelfHandling ev = new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                if (_scheduleSeq != seq) {
                    l.info("sched seq mismatch. no-op");
                    return;
                }

                _nextSchedule = Long.MAX_VALUE;

                assert !_ongoing;
                _ongoing = true;
                runAll_();
            }
        };

        l.info("schedule {} seq {}", delay, _scheduleSeq);

        if (delay > 0) {
            _f._sched.schedule(ev, delay);
        } else {
            _f._sched.schedule(ev);
        }
    }

    /**
     * The method runs all the scan sessions one after another. It schedules the current session and
     * returns if it can't be run immediately (due to failure, continuation, or required delays).
     */
    private void runAll_()
    {
        assert _ongoing;

        while (!_root.wasRemoved() && !_time2path.isEmpty()) {
            TimeKey tk = _time2path.firstKey();
            long now = _f._timeSource.getTime();
            if (tk._time > now) {
                // Reset _ongoing first so schedule_() won't be an no-op.
                _ongoing = false;
                schedule_(tk._time - now, tk._time);
                return;

            } else {
                // Must remove the entry from the queue _before_ running it, so that new
                // requests for the same PathKey can scheduled again, if they are added
                // while the the scan session is ongoing.
                assert !_time2path.isEmpty();
                PathKey pk = _time2path.remove(tk);
                assert pk != null;
                Util.verify(_path2time.remove(pk) == tk);

                final ScanSession ss = _f._factSS.create_(_root,
                        pk._absPaths, pk._recursive);
                if (!run_(tk, pk, ss)) return;
            }
        }

        List<ScanCompletionCallback> callbacks = _callbacks;
        _ongoing = false;
        _callbacks = Lists.newArrayList();

        for (final ScanCompletionCallback callback : callbacks) {
            _f._sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    callback.done_();
                }
            });
        }
    }

    /**
     * Execute a single scan session. If the session requires continuation, the method schedules it
     * as well as execution of subsequent sessions in the queue and returns. If the scan fails, the
     * method schedules an exponential retry and returns.
     *
     * @return whether the caller should run subsequent sessions in the queue.
     */
    private boolean run_(final TimeKey tk, final PathKey pk, final ScanSession ss)
    {
        if (_root.wasRemoved()) return true;

        try {
            if (ss.scan_()) {
                return true;

            } else {
                AbstractEBSelfHandling ev = new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        // N.B. run_() doesn't call schedule_() if the scan session fails. It relies
                        // on runAll_() to do the work.
                        if (run_(tk, pk, ss)) runAll_();
                    }
                };

                // Schedule instead of directly enqueueing to the core, as direct enqueueing can't
                // guarantee fairness for other events. Experiments show that direct enqueueing
                // could prevent other events to be executed by the core for an extended period of
                // time.
                _f._sched.schedule(ev, 0);
                return false;
            }
        } catch (SQLException e) {
            onException(e, tk, pk);
            return true;

        } catch (Exception e) {
            SystemUtil.fatalOnUncheckedException(e);
            onException(e, tk, pk);
            return true;
        }
    }

    private void onException(Exception e, final TimeKey tk, final PathKey pk)
    {
        defect.get().setMessage("scan exception retry")
                .setException(e)
                .sendAsync();

        // schedule an exponential retry and return
        long millisecondDelay = Math.max(tk._delay * 2, LibParam.EXP_RETRY_MIN_DEFAULT);
        millisecondDelay = Math.min(millisecondDelay, LibParam.EXP_RETRY_MAX_DEFAULT);
        l.warn("retry in {}", millisecondDelay, e);

        // re-enqueue the request. no need to schedule it since this method assumes that
        // scheduling will be done by the caller.
        enqueue_(pk, millisecondDelay);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps) throws Exception
    {
        for (Entry<TimeKey, PathKey> en : _time2path.entrySet()) {
            ps.println(indent + en.getKey() + ": " + en.getValue());
        }
    }
}
