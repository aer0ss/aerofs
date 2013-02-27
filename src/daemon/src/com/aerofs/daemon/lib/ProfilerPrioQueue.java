package com.aerofs.daemon.lib;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;

import com.aerofs.base.C;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.cfg.Cfg;

/**
 * I understand that implementation inheritance is not a good idea. because
 * this class is used only for profiling/debugging, I don't want to define a
 * PrioQueue interface just because for it, which would bring the overhead of
 * virtual function calls to each and every enqueue/dequeue operation.
 */
public class ProfilerPrioQueue<T> extends PrioQueue<T> {
    private static final Logger l = Util.l(ProfilerPrioQueue.class);

    private static final long STAT_INTERVAL = 5 * C.SEC;

    private class Stat {

        @SuppressWarnings("unchecked")
        private final Queue<Long>[] _qs = new Queue[Prio.values().length];

        private long _delayMin[] = new long[Prio.values().length];
        private long _delayMax[] = new long[Prio.values().length];
        private long _delayTotal[] = new long[Prio.values().length];
        private int _delayCnt[] = new int[Prio.values().length];

        private int _lenMin;
        private int _lenMax;
        private long _lenTotal;
        private int _lenCnt;

        Stat()
        {
            for (int i = 0; i < Prio.values().length; i++) {
                _qs[i] = new LinkedList<Long>();
            }

            reset_();
        }

        void reset_()
        {
            for (int i = 0; i < Prio.values().length; i++) {
                _delayMin[i] = Long.MAX_VALUE;
                _delayMax[i] = 0;
                _delayTotal[i] = 0;
                _delayCnt[i] = 0;
            }

            _lenMin = Integer.MAX_VALUE;
            _lenMax = 0;
            _lenTotal = 0;
            _lenCnt = 0;
        }

        void enqueue_(long now, Prio prio)
        {
            _qs[prio.ordinal()].add(now);
        }

        long dequeued_(long now, Prio prio)
        {
            int p = prio.ordinal();
            long time = now - _qs[p].remove();

            if (time < _delayMin[p]) _delayMin[p] = time;
            if (time > _delayMax[p]) _delayMax[p] = time;
            _delayTotal[p] += time;
            _delayCnt[p]++;

            int len = length_();
            if (len < _lenMin) _lenMin = len;
            if (len > _lenMax) _lenMax = len;
            _lenTotal += len;
            _lenCnt++;

            return time;
        }

        String getLenStat_()
        {
            return "len: " + _lenCnt + (_lenCnt == 0 ?
                " min - max - avg -" :
                " min " + _lenMin + " max " + _lenMax + " avg " +
                (_lenTotal / _lenCnt));
        }

        String getDelayStat_()
        {
            long min = Integer.MAX_VALUE;
            long max = 0;
            long total = 0;
            long cnt = 0;
            for (int i = 0; i < Prio.values().length; i++) {
                if (_delayMin[i] < min) min = _delayMin[i];
                if (_delayMax[i] > max) max = _delayMax[i];
                total += _delayTotal[i];
                cnt += _delayCnt[i];
            }

            return "delay: " + cnt + (cnt == 0 ?
                " min - max - avg -" :
                " min " + min + " max " + max + " avg " + (total / cnt));
        }

        String getStat_()
        {
            return getLenStat_() + " " + getDelayStat_();
        }

        void dumpStatMisc_(String indent, String indentUnit, PrintStream ps)
        {
            ps.println(indent + getLenStat_());
            for (int i = 0; i < Prio.values().length; i++) {
                ps.println(indent + Prio.values()[i] + ": " + _delayCnt[i] +
                        (_delayCnt[i] == 0 ? " min - max - avg -" :
                        (" min " + _delayMin[i] + " max " + _delayMax[i] + " avg " +
                        (_delayTotal[i] / _delayCnt[i]))));
            }
        }

    }

    private final Stat _statGlobal = new Stat();
    private final Stat _statCur = new Stat();
    private long _lastStat = System.currentTimeMillis();

    public ProfilerPrioQueue(int capacity)
    {
        super(capacity);

        assert Cfg.useProfiler();

    }

    public void enqueue_(T e, Prio prio)
    {
        super.enqueue_(e, prio);

        long now = System.currentTimeMillis();
        _statGlobal.enqueue_(now, prio);
        _statCur.enqueue_(now, prio);
    }

    public T dequeue_(OutArg<Prio> outPrio)
    {
        if (outPrio == null) outPrio = new OutArg<Prio>();
        T ret = super.dequeue_(outPrio);

        long now = System.currentTimeMillis();
        long time = _statGlobal.dequeued_(now, outPrio.get());
        _statCur.dequeued_(now, outPrio.get());

        if (time > Cfg.profilerStartingThreshold()) {
            l.warn("PROFILER PQ: " + time + " len " + length_() + " " +
                    ret.getClass());
        }

        if (now - _lastStat >= STAT_INTERVAL) {
            l.warn("PROFILER PQ STAT: " + now + " " + _statCur.getStat_());
            _statCur.reset_();
            _lastStat = now;
        }

        return ret;
    }

    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        super.dumpStatMisc(indent, indentUnit, ps);
        _statGlobal.dumpStatMisc_(indent, indentUnit, ps);
    }
}
