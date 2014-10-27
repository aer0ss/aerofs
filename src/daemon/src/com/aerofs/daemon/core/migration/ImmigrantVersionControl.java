package com.aerofs.daemon.core.migration;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.AbstractVersionControl;
import com.aerofs.daemon.core.store.IStoreCreationOperator;
import com.aerofs.daemon.core.store.MapSIndex2Contributors;
import com.aerofs.daemon.core.store.StoreCreationOperators;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.IImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.ImmigrantTickRow;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Map.Entry;

import org.slf4j.Logger;

/**
 * This class is very similar to NativeVersionControl in syntax, although
 * immigrant versions are an indirection of native versions.
 */

// TODO: caching

public class ImmigrantVersionControl extends AbstractVersionControl<ImmigrantTickRow>
    implements IStoreCreationOperator
{
    private static Logger l = Loggers.getLogger(ImmigrantVersionControl.class);

    private final IImmigrantVersionDatabase _ivdb;
    private final MapSIndex2Contributors _sidx2contrib;

    @Inject
    public ImmigrantVersionControl(IImmigrantVersionDatabase ivdb, CfgLocalDID cfgLocalDID,
            TransLocalVersionAssistant tlva, StoreCreationOperators sco, StoreDeletionOperators sdn,
            MapSIndex2Contributors sidx2contrib)
    {
        super(ivdb, cfgLocalDID, tlva, sdn);
        _ivdb = ivdb;
        _sidx2contrib = sidx2contrib;
        sco.add_(this);
    }

    @Override
    public void createStore_(SIndex sidx, boolean usePolaris, Trans t)
            throws SQLException
    {
        if (!usePolaris) restoreStore_(sidx, t);
    }

    public void createLocalImmigrantVersions_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        for (Entry<DID, Tick> en: v.getAll_().entrySet()) {
            Tick tick = en.getValue();
            if (!tick.equals(Tick.ZERO)) {
                updateMyImmigrantVersion_(socid, en.getKey(), tick, t);
            }
        }
    }

    private void updateMyImmigrantVersion_(SOCID socid, DID did, Tick tick, Trans t)
            throws SQLException
    {
        Tick immTick = _maxTick.incNonAlias();
        if (immigrantTickReceived_(socid, _cfgLocalDID.get(), immTick, did, tick, t)) {
            _ivdb.setGreatestTick_(immTick, t);
            _maxTick = immTick;
        }
    }

    /**
     * @return whether the tick was unknown
     */
    public boolean immigrantTickReceived_(SOCID socid, DID immDid, Tick immTick, DID did, Tick tick,
            Trans t) throws SQLException
    {
        if (!_ivdb.isTickKnown_(socid, did, tick)) {
            if (l.isDebugEnabled()) {
                l.debug("add imm ver" + socid + " imm " + immDid + immTick + " " + did + tick);
            }
            _ivdb.addImmigrantVersion_(socid, immDid, immTick, did, tick, t);
            _sidx2contrib.addContributor_(socid.sidx(), immDid, t);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void restoreTickRow_(SOCID socid, ImmigrantTickRow tr, Trans t) throws SQLException
    {
    }
}
