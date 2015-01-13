package com.aerofs.daemon.core.tc;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.admin.Dumpables;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.StrictLock;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.event.Prio;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;

/**
 * Acquire your tokens here, for a nominal fee
 */
public class TokenManager implements IDumpStatMisc
{
    private static final Logger l = Loggers.getLogger(TokenManager.class);

    static {
        // otherwise we have to change Category's implementation
        Preconditions.checkState(Prio.values().length == 2);
    }

    static interface ITokenUseListener
    {
        void prePause_(TCB tcb, Token tk, String reason) throws ExAborted;
        void postPause_(TCB tcb) throws ExAborted;
    }

    private static class CatInfo
    {
        private final Cat _cat;
        private final int _quota;
        private int _total;
        private final Set<Token> _lo = Sets.newLinkedHashSet();
        // for dumping only
        private final Set<Token> _hi = Sets.newHashSet();

        private final Runnable _reclaimNotifier = new Runnable() {
            @Override
            public void run()
            {
                // if the reclamation listener does not acquire a token we need
                // to recursively notify other waiting listeners, otherwise we
                // may end up with a bunch of listeners waiting despite tokens
                // being available.
                if (_total < _quota) {
                    ITokenReclamationListener l = _ls.removeFirstListener_();
                    if (l != null) l.tokenReclaimed_(this);
                }
            }
        };

        private final EnumMap<Prio, Set<Token>> _tokenSetMap = new EnumMap<Prio, Set<Token>>(
            Prio.class);
        {
            _tokenSetMap.put(Prio.LO, _lo);
            _tokenSetMap.put(Prio.HI, _hi);
        }

        private final ConcurrentlyModifiableListeners<ITokenReclamationListener> _ls =
                ConcurrentlyModifiableListeners.create();

        private CatInfo(Cat cat, int quota)
        {
            _cat = cat;
            _quota = quota;
        }
    }

    private final StrictLock _l;

    private ITokenUseListener _listener;
    private final EnumMap<Cat, CatInfo> _cat2info = Maps.newEnumMap(Cat.class);

    @Inject
    public TokenManager(CoreQueue q)
    {
        _l = q.getLock();

        for (Cat cat : Cat.values()) _cat2info.put(cat, new CatInfo(cat, getQuota(cat)));

        Dumpables.add("cat", this);
    }

    void setListener_(ITokenUseListener listener)
    {
        Preconditions.checkState(_listener == null);
        _listener = Preconditions.checkNotNull(listener);
    }

    private static int getQuota(@Nonnull Cat cat)
    {
        switch (cat) {
        case CLIENT:
            return Cfg.db().getInt(Key.MAX_CLIENT_STACKS);
        case SERVER:
            return Cfg.db().getInt(Key.MAX_SERVER_STACKS);
        case API_UPLOAD:
            return Cfg.db().getInt(Key.MAX_API_UPLOADS);
        case HOUSEKEEPING:
            return Cfg.db().getInt(Key.MAX_HOUSEKEEPING_STACKS);
        case RESOLVE_USER_ID:
            return Cfg.db().getInt(Key.MAX_D2U_STACKS);
        case UNLIMITED:
            return Integer.MAX_VALUE;
        default:
            throw new UnsupportedOperationException("Unknown Cat: " + cat);
        }
    }

    /**
     * Acquire a token or complain loudly
     * @throws ExNoResource if the cat is full
     */
    public Token acquireThrows_(Cat cat, String reason) throws ExNoResource
    {
        Token tk = acquire_(cat, reason);
        if (tk == null) {
            CatInfo catInfo = Preconditions.checkNotNull(_cat2info.get(cat));

            throw new ExNoResource(cat + " full: toks:" + dumpCatInfo(catInfo, ""));
        }
        return tk;
    }

    /**
     * Try to acquire a token for pausing/sleeping
     * @return null if the category is full
     */
    public @Nullable Token acquire_(Cat cat, String reason)
    {
        CatInfo info = _cat2info.get(cat);
        Prio prio = TC.currentThreadPrio();

        if (info._total == info._quota) {
            if (prio == Prio.LO) {
                return null;
            } else {
                Preconditions.checkState(prio == Prio.HI);
                if (info._lo.isEmpty()) return null;
                Iterator<Token> iter = info._lo.iterator();
                if (!iter.hasNext()) return null;
                Token preempt = iter.next();
                l.debug("{} preempts {}", info._cat, preempt);
                preempt.reclaim_(false);
            }
        }

        Preconditions.checkState(info._total < info._quota);
        info._total++;

        Token tk = new Token(this, info._cat, prio, reason);
        Util.verify(info._tokenSetMap.get(prio).add(tk));
        return tk;
    }

    @FunctionalInterface
    public interface Closure<T, E extends Throwable>
    {
        T run() throws E;
    }

    final public <T, E extends Exception> T inPseudoPause_(Cat cat, String reason, Closure<T, E> r)
            throws E, ExNoResource, ExAborted
    {
        try (Token tk = acquireThrows_(cat, reason)) {
            return tk.inPseudoPause_(r);
        }
    }

    void reclaim_(Token tk, Prio prio, boolean notifyReclaimListener)
    {
        final CatInfo info = _cat2info.get(tk.getCat());
        Preconditions.checkState(info._total > 0);
        info._total--;

        Util.verify(info._tokenSetMap.get(prio).remove(tk));
        if (notifyReclaimListener) {
            info._reclaimNotifier.run();
        }
    }


    /**
     * Add a listener for when a token gets reclaimed and space is available
     */
    public void addTokenReclamationListener_(Cat cat, ITokenReclamationListener l)
    {
        _cat2info.get(cat)._ls.addListener_(l);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        for (CatInfo catInfo : _cat2info.values()) {
            ps.print(dumpCatInfo(catInfo, indent));
        }
    }

    private String dumpCatInfo(CatInfo catInfo, String indent)
    {
        StringBuilder sb = new StringBuilder();

        for (Iterable<Token> tokens : catInfo._tokenSetMap.values()) {
            for (Token token : tokens) {
                sb.append(indent).append(token).append(", ");
            }
        }

        return sb.toString();
    }


    void pauseImpl_(Token tk, String reason) throws ExAborted
    {
        TCB tcb = TC.tcb();
        _listener.prePause_(tcb, tk, reason);
        try {
            boolean taskCompleted = tcb.park_(tk.getCat(), TC.FOREVER);
            Preconditions.checkState(taskCompleted, tcb + " " + tk.getCat());
        } finally {
            _listener.postPause_(tcb);
        }
    }

    void pauseImpl_(Token tk, long timeout, String reason) throws ExAborted, ExTimeout
    {
        TCB tcb = TC.tcb();
        _listener.prePause_(tcb, tk, reason);
        boolean taskCompleted;
        try {
            taskCompleted = tcb.park_(tk.getCat(), timeout);
        } finally {
            // abortion checking must precede timeout checking, since in some use
            // cases the application depends on the fact that abort signals can be
            // reliably delivered to the receiving thread (cf. Download)
            _listener.postPause_(tcb);
        }

        if (!taskCompleted) {
            l.debug("timed out");
            throw new ExTimeout();
        }
    }

    TCB pseudoPauseImpl_(Token tk, String reason) throws ExAborted
    {
        TCB tcb = TC.tcb();
        _listener.prePause_(tcb, tk, reason);
        _l.unlock();
        return tcb;
    }

    void sleepImpl_(Token tk, long timeout, String reason) throws ExAborted
    {
        TCB tcb = tk.pseudoPause_("sleep: " + reason);
        try {
            ThreadUtil.sleepUninterruptable(timeout);
        } finally {
            tcb.pseudoResumed_();
        }
    }
}
