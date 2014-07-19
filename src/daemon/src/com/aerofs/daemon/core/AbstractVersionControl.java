package com.aerofs.daemon.core;

import java.sql.SQLException;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.trans.Trans;
import org.slf4j.Logger;

import com.aerofs.daemon.lib.db.ver.IVersionDatabase;
import com.aerofs.daemon.lib.db.ver.AbstractTickRow;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nonnull;

/**
 * The *VersionControl's are thin wrappers for the implementations of
 * IVersionDatabase. Caching-support to come.
 * @param <E> specializes AbstractVersionControl for Native or ImmigrantTickRows
 */
public abstract class AbstractVersionControl<E extends AbstractTickRow>
        implements IStoreDeletionOperator
{
    private static final Logger l = Loggers.getLogger(AbstractVersionControl.class);

    private final IVersionDatabase<E> _vdb;
    protected final CfgLocalDID _cfgLocalDID;
    protected final TransLocalVersionAssistant _tlva;
    protected Tick _maxTick;

    protected AbstractVersionControl(IVersionDatabase<E> vdb, CfgLocalDID cfgLocalDID,
            TransLocalVersionAssistant tlva, StoreDeletionOperators sdo)
    {
        _vdb = vdb;
        _cfgLocalDID = cfgLocalDID;
        _tlva = tlva;
        sdo.addDeferred_(this);
    }

    public void init_() throws SQLException
    {
        _maxTick = _vdb.getGreatestTick_();
    }

    public @Nonnull Version getKnowledgeExcludeSelf_(SIndex sidx) throws SQLException
    {
        return _vdb.getKnowledgeExcludeSelf_(sidx);
    }

    public void addKnowledge_(SIndex sidx, DID did, Tick tick, Trans t)
        throws SQLException
    {
        assert !did.equals(_cfgLocalDID.get());
        assert !tick.equals(Tick.ZERO);
        _vdb.addKnowledge_(sidx, did, tick, t);
        if (l.isDebugEnabled())
            l.debug(this.getClass() + " add kwlg " + sidx + " " + did + tick);
    }

    public @Nonnull IDBIterator<E> getMaxTicks_(SIndex sidx, DID did, Tick from) throws SQLException
    {
        return _vdb.getMaxTicks_(sidx, did, from);
    }

    /**
     * Deletes all version records for store s, after having backed up
     * the ticks for *this* DID
     */
    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        l.debug("Delete store {}", sidx);

        _vdb.deleteTicksAndKnowledgeForStore_(sidx, t);
    }

    /**
     * Restores the version ticks for *this* DID to the relevant version table
     * (native or immigrant), for Store s. This method is a template algorithm;
     * subclasses must restore each Tick Row in their own specialized way.
     *
     * MUST not be called on an unclean store.
     */
    public void restoreStore_(SIndex sidx, Trans t) throws SQLException
    {
        l.debug("Restore store {}", sidx);

        IDBIterator<E> iter = _vdb.getBackupTicks_(sidx);
        try {
            while (iter.next_()) {
                E tr = iter.get_();
                SOCID socid = new SOCID(sidx, tr._oid, tr._cid);

                // All ticks received from Backup DB should have been unknown
                // otherwise the deleteTicksFromStore is not working, or
                // deleteStore_ was not called before this method
                assert !_vdb.isTickKnown_(socid, _cfgLocalDID.get(), tr._tick);

                restoreTickRow_(socid, tr, t);
            }
        } finally {
            iter.close_();
        }
    }

    /**
     * Restore the given tick row to the version table. This method helps to specialize
     * restoreStore_ above.
     */
    protected abstract void restoreTickRow_(SOCID socid, E tr, Trans t) throws SQLException;
}
