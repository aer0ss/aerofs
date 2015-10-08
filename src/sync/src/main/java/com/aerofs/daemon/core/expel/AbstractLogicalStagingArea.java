package com.aerofs.daemon.core.expel;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.CleanupScheduler;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.SID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.core.expel.LogicalStagingAreaDatabase.*;
import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractLogicalStagingArea implements CleanupScheduler.CleanupHandler, IStartable
{
    private final static Logger l = Loggers.getLogger(AbstractLogicalStagingArea.class);

    // relinquish the core lock every time a cleanup operation takes more than 100ms
    static  final long RESCHEDULE_THRESHOLD = 100;
    static final long SPLIT_TRANS_THRESHOLD = 2000;

    protected final IMapSIndex2SID _sidx2sid;
    protected final LogicalStagingAreaDatabase _sadb;
    protected final CleanupScheduler _sas;
    protected final StoreDeletionOperators _storeDeletionOperators;
    protected final TransManager _tm;
    protected final IPhysicalStorage _ps;

    public AbstractLogicalStagingArea(IMapSIndex2SID sidx2sid, LogicalStagingAreaDatabase sadb,
            CoreScheduler sched, StoreDeletionOperators storeDeletionOperators, TransManager tm,
            IPhysicalStorage ps)
    {
        _sadb = sadb;
        _sidx2sid = sidx2sid;
        _sas = new CleanupScheduler(this, sched);
        _storeDeletionOperators = storeDeletionOperators;
        _tm = tm;
        _ps = ps;
    }

    @Override
    public String name()
    {
        return "logical-staging";
    }

    @Override
    public void start_()
    {
        _sas.schedule_();
    }

    @Override
    public boolean process_() throws Exception
    {
        IDBIterator<LogicalStagingAreaDatabase.StagedFolder> it = _sadb.listEntries_();
        try {
            ElapsedTimer timer = new ElapsedTimer();
            while (it.next_()) {
                processStagedFolder_(it.get_());
                // if cleanup is taking too long, reschedule to
                // allow other core threads to make progress
                if (timer.elapsed() > RESCHEDULE_THRESHOLD) return true;
            }
        } finally {
            it.close_();
        }
        l.info("logical staging area empty");
        return false;
    }

    protected boolean isStaged_(SOID soid)  throws SQLException
    {
        return _sadb.historyPath_(soid) != null;
    }

    /**
     * If a store is expelled it won't show up in the (in-memory) SIndex<->SID map
     */
    protected boolean isStoreStaged_(SIndex sidx)
    {
        return _sidx2sid.getNullable_(sidx) == null;
    }

    protected void finalize_(SOID soid, SID physicalRoot, Trans t) throws SQLException, IOException
    {
        l.info("cleaned {}", soid);
        _sadb.removeEntry_(soid, t);

        // if the whole store was staged and we just processed  the last staged
        // folder in said store then it is time to perform store-wide, post-cleanup
        // tasks
        SIndex sidx = soid.sidx();
        if (isStoreStaged_(sidx) && !_sadb.hasMoreEntries_(sidx)) {
            finalizeStoreCleanup_(sidx, physicalRoot, t);
        }
    }

    protected void finalizeStoreCleanup_(SIndex sidx, SID physicalRoot, Trans t)
            throws SQLException, IOException
    {
        l.info("finalize store cleanup {}", sidx);
        SID sid = _sidx2sid.getAbsent_(sidx);
        _ps.deleteStore_(physicalRoot, sidx, sid, t);
        _storeDeletionOperators.runAllDeferred_(sidx, t);
    }

    /**
     * Ensures that a given store is clean.
     *
     * This will cause a synchronous cleanup if the store was not already clean.
     */
    public void ensureStoreClean_(SIndex sidx, Trans t) throws Exception
    {
        l.info("ensure clean {}", sidx);

        IDBIterator<StagedFolder> it = _sadb.listEntriesByStore_(sidx);
        try {
            while (it.next_()) {
                StagedFolder f = it.get_();
                immediateCleanup_(f, t);
            }
        } finally {
            it.close_();
        }
        checkState(!_sadb.hasMoreEntries_(sidx));
    }

    abstract protected void immediateCleanup_(StagedFolder f, Trans t) throws Exception;
    abstract protected void processStagedFolder_(StagedFolder it_) throws SQLException, IOException;

    public abstract void stageCleanup_(SOID soid, ResolvedPath pathOld, @Nullable String rev, Trans t) throws Exception;
}
