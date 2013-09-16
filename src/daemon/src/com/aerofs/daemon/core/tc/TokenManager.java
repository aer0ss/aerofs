package com.aerofs.daemon.core.tc;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.aerofs.base.Loggers;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.event.Prio;
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
    static {
        // otherwise we have to change Category's implementation
        assert Prio.values().length == 2;
    }

    private static class CatInfo
    {
        private final Cat _cat;
        private final int _quota;
        private int _total;
        private final Set<Token> _lo = new LinkedHashSet<Token>();
        // for dumping only
        private final Set<Token> _hi = new HashSet<Token>();

        private final EnumMap<Prio, Set<Token>> _tokenSetMap = new EnumMap<Prio, Set<Token>>(
            Prio.class);
        {
            _tokenSetMap.put(Prio.LO, _lo);
            _tokenSetMap.put(Prio.HI, _hi);
        }

        private final ConcurrentlyModifiableListeners<ITokenReclamationListener> _ls = ConcurrentlyModifiableListeners
                .create();

        private CatInfo(Cat cat, int quota)
        {
            _cat = cat;
            _quota = quota;
        }
    }

    private static final Logger l = Loggers.getLogger(TokenManager.class);

    private TC _tc;
    private final EnumMap<Cat, CatInfo> _cat2info = new EnumMap<Cat, CatInfo>(Cat.class);

    @Inject
    public void inject_(TC tc)
    {
        _tc = tc;

        for (Cat cat : Cat.values()) _cat2info.put(cat, new CatInfo(cat, getQuota(cat)));
    }

    private static int getQuota(@Nonnull Cat cat)
    {
        switch (cat) {
        case CLIENT:
            return Cfg.db().getInt(Key.MAX_CLIENT_STACKS);
        case SERVER:
            return Cfg.db().getInt(Key.MAX_SERVER_STACKS);
        case HOUSEKEEPING:
            return Cfg.db().getInt(Key.MAX_HOUSEKEEPING_STACKS);
        case DID2USER:
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
            CatInfo catInfo = _cat2info.get(cat);
            assert catInfo != null;

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
        Prio prio = _tc.prio();

        if (info._total != info._quota) {
            if (info._total > info._quota) throw new IllegalStateException();
        } else {
            if (prio == Prio.LO) {
                return null;
            } else {
                assert prio == Prio.HI;
                if (info._lo.isEmpty()) return null;
                Iterator<Token> iter = info._lo.iterator();
                if (!iter.hasNext()) return null;
                Token preempt = iter.next();
                l.debug(info._cat + " preempts " + preempt);
                preempt.reclaim_(false);
            }
        }

        info._total++;

        Token tk = new Token(this, _tc, info._cat, prio, reason);
        Util.verify(info._tokenSetMap.get(prio).add(tk));
        return tk;
    }

    void reclaim_(Token tk, Prio prio, boolean notifyReclaimListener)
    {
        CatInfo info = _cat2info.get(tk.getCat());
        assert info._total > 0;
        info._total--;

        Util.verify(info._tokenSetMap.get(prio).remove(tk));
        if (notifyReclaimListener) {
            ITokenReclamationListener l = info._ls.removeFirstListener_();
            if (l != null) l.tokenReclaimed_();
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
}
