/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryServiceAdapter;
import com.aerofs.daemon.core.persistency.IPersistentQueue;
import com.aerofs.daemon.core.persistency.PersistentQueueDriver;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

/**
 * When an anchor is deleted, the local user should automatically leave the corresponding shared
 * folder (mostly to avoid automatically re-joining it when a new device is installed).
 */
class SharedFolderAutoLeaver extends DirectoryServiceAdapter
{
    private final Logger l = Loggers.getLogger(SharedFolderAutoLeaver.class);

    private final DirectoryService _ds;
    private final SharedFolderLeaveQueueDatabase _lqdb;

    class LeaveQueue implements IPersistentQueue<SID, SID>
    {
        @Override
        public void enqueue_(SID sid, Trans t) throws SQLException
        {
            _lqdb.addLeavecommand_(sid, t);
        }

        @Override
        public SID front_() throws SQLException
        {
            IDBIterator<SID> it = _lqdb.getLeaveCommands_();
            try {
                return it.next_() ? it.get_() : null;
            } finally {
                it.close_();
            }
        }

        @Override
        public boolean process_(SID sid, Token tk) throws Exception
        {
            TCB tcb = tk.pseudoPause_("sp-leave");
            try {
                try {
                    newMutualAuthClientFactory().create()
                            .signInRemote()
                            .leaveSharedFolder(sid.toPB());
                } catch (ExNotFound e) {
                    l.info("Not a member, ignore leave command: " + sid);
                } catch (ExBadArgs e) {
                    l.info("Root folder, ignore leave command: " + sid);
                }
                l.info("auto-leave.done " + sid);
                return true;
            } finally {
                tcb.pseudoResumed_();
            }
        }

        @Override
        public void dequeue_(SID sid, Trans t) throws SQLException
        {
            _lqdb.removeLeaveCommands_(sid, t);
        }
    }

    private final PersistentQueueDriver<SID, SID> _pqd;

    @Inject
    public SharedFolderAutoLeaver(PersistentQueueDriver.Factory f, DirectoryService ds,
            SharedFolderLeaveQueueDatabase lqdb)
    {
        _ds = ds;
        _lqdb = lqdb;
        _pqd = f.create(new LeaveQueue());

        _ds.addListener_(this);

        _pqd.scheduleScan_();
    }

    public void removeFromQueue_(SID sid, Trans t) throws SQLException
    {
        _lqdb.removeLeaveCommands_(sid, t);
    }

    private final TransLocal<Set<SID>> _tlSID = new TransLocal<Set<SID>>()
    {
        @Override
        protected Set<SID> initialValue(Trans t)
        {
            final Set<SID> sids = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    if (l.isDebugEnabled()) for (SID sid : sids) l.debug("auto-leave " + sid);
                    _pqd.scheduleScan_();
                }
            });
            return sids;
        }
    };

    @Override
    public void objectExpelled_(SOID soid, Trans t) throws SQLException
    {
        if (!(soid.oid().isAnchor() && _ds.isDeleted_(_ds.getOA_(soid)))) return;

        SID sid = SID.anchorOID2storeSID(soid.oid());
        _pqd.enqueue_(sid, t);
        _tlSID.get(t).add(sid);
    }
}
