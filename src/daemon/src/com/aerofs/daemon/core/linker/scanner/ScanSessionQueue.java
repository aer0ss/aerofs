package com.aerofs.daemon.core.linker.scanner;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.linker.scanner.ScanSession.Factory;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.injectable.InjectableSystem;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.log4j.Logger;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Map.Entry;

public class ScanSessionQueue implements IDumpStatMisc
{
    private static Logger l = Util.l(ScanSessionQueue.class);
    private static FrequentDefectSender fds = new FrequentDefectSender();

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
            int comp = Util.compare(_time, timeKey._time);
            return comp == 0 ? Util.compare(_seq, timeKey._seq) : comp;
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
            return FluentIterable.from(ImmutableSet.copyOf(_absPaths))
                    .transform(new Function<String, String>()
                    {
                        @Nullable
                        @Override
                        public String apply(@Nullable String path)
                        {
                            if (path != null) {
                                return ObfuscatingFormatters.obfuscatePath(path);
                            }
                            return null;
                        }
                    })
                    .toString() + ":" + _recursive;
        }
    }

    private final TC _tc;
    private final CoreScheduler _sched;
    private final ScanSession.Factory _factSS;
    private final InjectableSystem _sys;

    private final SortedMap<TimeKey, PathKey> _time2path = Maps.newTreeMap();
    private final Map<PathKey, TimeKey> _path2time = Maps.newHashMap();

    // the starting time for the next scan session
    private long _nextSchedule = Long.MAX_VALUE;

    private int _scheduleSeq;

    // whether there is an ongoing scan session
    private boolean _ongoing;

    @Inject
    public ScanSessionQueue(TC tc, CoreScheduler sched, Factory factSS, InjectableSystem sys)
    {
        _tc = tc;
        _sched = sched;
        _factSS = factSS;
        _sys = sys;
    }

    public void recursiveScanImmediately_(Set<String> absPaths,
            @Nonnull ScanCompletionCallback callback)
    {
        scanImpl_(new PathKey(absPaths, true), 0, callback);
    }

    public void scanImmediately_(Set<String> absPaths, boolean recursive)
    {
        scanImpl_(new PathKey(absPaths, recursive), 0, new ScanCompletionCallback());
    }

    public void scanAfterDelay_(Set<String> absPaths, boolean recursive)
    {
        scanImpl_(new PathKey(absPaths, recursive), Param.EXP_RETRY_MIN_DEFAULT,
                new ScanCompletionCallback());
    }

    /**
     * @param delay relative delay before running the requested scan
     *
     * N.B this method is available only to core threads
     */
    private void scanImpl_(PathKey pk, long delay, @Nonnull ScanCompletionCallback callback)
    {
        assert delay >= 0;
        assert _tc.isCoreThread();

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
        long time = _sys.currentTimeMillis() + millisecondDelay;

        boolean replace = tkOld != null && tkOld._time > time;
        if (replace) {
            // The value removed from _path2time must be the tkOld itself, whereas the value removed
            // from _time2path can be (and should be) a different but equivalent object.
            Util.verify(_path2time.remove(pk) == tkOld);
            Util.verify(_time2path.remove(tkOld).equals(pk));
        }

        if (replace || tkOld == null) {
            l.warn("enq " + pk + " in " + millisecondDelay + " replace " + replace);
            TimeKey tk = new TimeKey(time, millisecondDelay);
            Util.verify(_path2time.put(pk, tk) == null);
            Util.verify(_time2path.put(tk, pk) == null);
        }

        return time;
    }

    /**
     * Schedule an event that runs the next scan session. No-op if the next session has been
     * scheduled.
     *
     * @param delay relative timeout
     * @param time absolute timeout
     */
    private void schedule_(long delay, long time, final ScanCompletionCallback callback)
    {
        assert delay >= 0;

        if (_ongoing || _nextSchedule <= time) {
            l.info("skip schedule " + delay + " " + time + " " + _ongoing + " " + _nextSchedule);
            return;
        }

        _nextSchedule = time;
        final int seq = ++_scheduleSeq;

        l.info("schedule " + delay + " seq " + _scheduleSeq);

        _sched.schedule(new AbstractEBSelfHandling() {
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
                runAll_(callback);
            }
        }, delay);
    }

    /**
     * The method runs all the scan sessions one after another. It schedules the current session and
     * returns if it can't be run immediately (due to failure, continuation, or required delays).
     */
    private void runAll_(final ScanCompletionCallback callback)
    {
        assert _ongoing;

        while (true) {
            if (_time2path.isEmpty()) {
                _ongoing = false;
                _sched.schedule(new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        callback.done_();
                    }
                }, 0);
                return;
            }

            TimeKey tk = _time2path.firstKey();
            long now = _sys.currentTimeMillis();
            if (tk._time > now) {
                // Reset _ongoing first so schedule_() won't be an no-op.
                _ongoing = false;
                schedule_(tk._time - now, tk._time, callback);
                return;

            } else {
                // Must remove the entry from the queue _before_ running it, so that new
                // requests for the same PathKey can scheduled again, if they are added
                // while the the scan session is ongoing.
                assert !_time2path.isEmpty();
                PathKey pk = _time2path.remove(tk);
                assert pk != null;
                Util.verify(_path2time.remove(pk) == tk);

                final ScanSession ss = _factSS.create_(pk._absPaths, pk._recursive);
                if (!run_(tk, pk, ss, callback)) return;
            }
        }
    }

    /**
     * Execute a single scan session. If the session requires continuation, the method schedules it
     * as well as execution of subsequent sessions in the queue and returns. If the scan fails, the
     * method schedules an exponential retry and returns.
     *
     * @return whether the caller should run subsequent sessions in the queue.
     */
    private boolean run_(final TimeKey tk, final PathKey pk, final ScanSession ss,
            final ScanCompletionCallback callback)
    {
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
                        if (run_(tk, pk, ss, callback)) runAll_(callback);
                    }
                };

                // Schedule instead of directly enqueueing to the core, as direct enqueueing can't
                // guarantee fairness for other events. Experiments show that direct enqueueing
                // could prevent other events to be executed by the core for an extended period of
                // time.
                _sched.schedule(ev, 0);
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
        fds.logSendAsync("scan exception retry", e);

        // schedule an exponential retry and return
        long millisecondDelay = Math.max(tk._delay * 2, Param.EXP_RETRY_MIN_DEFAULT);
        millisecondDelay = Math.min(millisecondDelay, Param.EXP_RETRY_MAX_DEFAULT);
        l.warn("retry in " + millisecondDelay + ": " + Util.e(e));

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
