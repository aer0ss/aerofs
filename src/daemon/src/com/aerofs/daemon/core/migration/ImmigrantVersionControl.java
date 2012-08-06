package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.AbstractVersionControl;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.IImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.ImmigrantTickRow;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;

import com.google.inject.Inject;

import java.sql.SQLException;

import org.apache.log4j.Logger;

/**
 * This class is very similar to NativeVersionControl in syntax, although
 * immigrant versions are an indirection of native versions.
 */

// TODO: caching

public class ImmigrantVersionControl extends AbstractVersionControl<ImmigrantTickRow>
{
    private static Logger l = Util.l(ImmigrantVersionControl.class);

    private final IImmigrantVersionDatabase _ivdb;

    @Inject
    public ImmigrantVersionControl(IImmigrantVersionDatabase ivdb, CfgLocalDID cfgLocalDID,
            TransLocalVersionAssistant tlva)
    {
        super(ivdb, cfgLocalDID, tlva);
        _ivdb = ivdb;
    }

    public void updateMyImmigrantVersion_(SOCID socid, DID did, Tick tick, Trans t)
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
            if (l.isInfoEnabled()) {
                l.info("add imm ver" + socid + " imm " + immDid + immTick + " " + did + tick);
            }
            _ivdb.addImmigrantVersion_(socid, immDid, immTick, did, tick, t);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void restoreTickRow_(SOCID socid, ImmigrantTickRow tr, Trans t) throws SQLException
    {
        _ivdb.addImmigrantVersion_(socid, _cfgLocalDID.get(), tr._immTick, tr._did, tr._tick, t);
    }
}
