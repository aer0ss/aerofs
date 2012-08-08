package com.aerofs.daemon.core;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.proto.NewUpdates;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.lib.db.trans.Trans;
import org.apache.log4j.Logger;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.google.common.collect.Maps;

// TODO don't include KIndex in parameters, writing non-master branches is
// not allowed at all.

public class ComMonitor implements IDumpStatMisc
{
    private static final Logger l = Util.l(ComMonitor.class);

    private final Map<SOCKID, ComState> _map = Maps.newTreeMap();

    private DelayedScheduler _dsScan;
    private CoreScheduler _sched;
    private NativeVersionControl _nvc;
    private NewUpdates _nu;
    private DirectoryService _ds;

    @Inject
    public void inject_(NewUpdates nu, NativeVersionControl nvc, DirectoryService ds,
            CoreScheduler sched)
    {
        _nu = nu;
        _nvc = nvc;
        _ds = ds;
        _sched = sched;

        _dsScan = new DelayedScheduler(_sched, DaemonParam.CM_SCAN_DELAY, new Runnable() {
            @Override
            public void run()
            {
                List<SOCKID> updated = new ArrayList<SOCKID>();

                Iterator<Entry<SOCKID, ComState>> it = _map.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<SOCKID, ComState> en = it.next();
                    SOCKID k = en.getKey();
                    ComState com = en.getValue();

                    // can't exchange the positions of the two 'if' blocks,
                    // as writes concurrent with us may increment write
                    // counts between the two blocks. The current code
                    // catches any write in between the second block and the
                    // first one in the next round of scanning
                    if (com.getWriterCount_() + com.getReaderCount_() == 0) {
                        it.remove();

                        if (com.hasMoreWrites_()) updated.add(k);
                    }
                }

                if (!updated.isEmpty()) {
                    try {
                        _nu.send_(updated);
                    } catch (Exception e) {
                        l.warn("send NEW_UPDATES: " + Util.e(e));
                    }
                }
            }
        });
    }

    // Use this method to assign new version for all non-aliased objects.
    public void atomicWrite_(SOCKID k, Trans t)
        throws ExNotFound, SQLException, IOException
    {
        atomicWriteImpl_(k, false, t);
    }

    // Use this method to assign new version for aliased objects.
    public void atomicWriteAliased_(SOCKID k, Trans t)
        throws ExNotFound, SQLException, IOException
    {
        atomicWriteImpl_(k, true, t);
    }

    private void atomicWriteImpl_(SOCKID k, boolean alias, Trans t)
        throws ExNotFound, SQLException, IOException
    {
        l.info("Atomic write for: " + k);
        ComState ws = beginWrite_(k, t);
        if (ws.preWrite()) handlePreWriteEvent_(k, alias, t);
        endWrite_(k, t);
    }

    /**
     * when the method returns, write count of the component is set to one
     *
     * @param t may be null if transaction hasn't started
     */
    public ComState beginWrite_(SOCKID k, Trans t)
    {
        // the following code won't be executed upon SQL exceptions
        ComState cs = _map.get(k);
        if (cs == null) {
            cs = new ComState();
            _map.put(k, cs);
        }
        cs.addWriter_();

        return cs;
    }

    public void endWrite_(SOCKID k, Trans t)
        throws SQLException, ExNotFound, IOException
    {
        assert t != null;

        _map.get(k).removeWriter_();
        _dsScan.schedule_();

        if (!k.cid().isMeta()) {
            // Update length and mtime on the logical file to be consistent with the physical file.
            // The linker relies on these fields to detect file changes.
            IPhysicalFile pf = _ds.getOA_(k.soid()).ca(k.kidx()).physicalFile();

            long mtime = pf.getLastModificationOrCurrentTime_();
            // We are about to set a null hash, which is allowed only on master branches. See Hasher
            // for detail.
            assert k.kidx().equals(KIndex.MASTER) : k;
            _ds.setCA_(k.sokid(), pf.getLength_(), mtime, null, t);
        }
    }

    public void beginRead_(SOCKID k) throws Exception
    {
        ComState cs = _map.get(k);
        if (cs == null) {
            cs = new ComState();
            _map.put(k, cs);
        }

        cs.addReader_();
    }

    public void endRead_(SOCKID k)
    {
        _map.get(k).removeReader_();
        _dsScan.schedule_();
    }

    public int getWritersCount_(SOCKID k)
    {
        ComState cs = _map.get(k);
        return cs == null ? 0 : cs.getWriterCount_();
    }

    public int getReadersCount_(SOCKID k)
    {
        ComState cs = _map.get(k);
        return cs == null ? 0 : cs.getReaderCount_();
    }

    public void handlePreWriteEvent_(SOCKID k, boolean alias, Trans t)
            throws SQLException, ExNotFound
    {
        ComState cs = _map.get(k);
        assert cs != null;

        _nvc.updateMyVersion_(k, alias, t);

        cs.generateVersionOnNextWrite_(false);
        l.info("Version updated for sockid:" + k);
    }

    /**
     * Get write count and freeze current version
     *
     * When a version goes public (i.e. published), it is finalized, containing
     * all the previous writes. a new version must be generated to include
     * future writes. Because the system may crash after writes are made and
     * before a new version is committed to the db which causes inconsistency,
     * we have to generate the version before the next write.
     *
     * @param k
     * @return the current write count
     */
    public int versionPublished_(SOCKID k)
    {
        ComState cs = _map.get(k);
        if (cs == null) {
            return 0;
        } else {
            cs.generateVersionOnNextWrite_(true);
            return cs.getWriteCount();
        }
    }

    public boolean hasMoreWritesSince_(SOCKID k, Version v, int wc)
        throws SQLException
    {
        ComState cs = _map.get(k);
        if (cs != null) {
            // can't use '>' here for two reasons:
            //  1) wc may overflow
            //  2) the ComState object may be a different one from which the wc is
            //     sampled at
            if (cs.getWriteCount() != wc) return true;
        }

        Version vNow = _nvc.getLocalVersion_(k);
        if (!vNow.sub_(v).isZero_()) return true;

        return false;
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        for (Entry<SOCKID, ComState> en : _map.entrySet()) {
            ps.println(indent + en.getKey() + ": " + en.getValue());
        }
    }
}
