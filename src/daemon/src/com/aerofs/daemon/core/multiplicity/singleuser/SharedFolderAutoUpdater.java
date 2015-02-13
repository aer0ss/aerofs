/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryServiceAdapter;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.expel.Expulsion.IExpulsionListener;
import com.aerofs.daemon.core.multiplicity.singleuser.ISharedFolderOp.SharedFolderOpType;
import com.aerofs.daemon.core.persistency.IPersistentQueue;
import com.aerofs.daemon.core.persistency.PersistentQueueDriver;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SOID;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

/**
 * When an anchor is deleted, the local user should automatically leave the corresponding shared
 * folder (mostly to avoid automatically re-joining it when a new device is installed).
 * When anchor is renamed it triggers shared folder name update in db for this particular user
 */
class SharedFolderAutoUpdater extends DirectoryServiceAdapter implements IExpulsionListener
{
    private final Logger l = Loggers.getLogger(SharedFolderAutoUpdater.class);

    private final DirectoryService _ds;
    private final SharedFolderUpdateQueueDatabase _lqdb;

    class LeaveQueue implements IPersistentQueue<ISharedFolderOp, ISharedFolderOp>
    {
        @Override
        public void enqueue_(ISharedFolderOp op, Trans t) throws SQLException
        {
            _lqdb.addCommand_(op, t);
        }

        @Override
        public ISharedFolderOp front_() throws SQLException
        {
            IDBIterator<ISharedFolderOp> it = _lqdb.getCommands_();
            try {
                return it.next_() ? it.get_() : null;
            } finally {
                it.close_();
            }
        }

        @Override
        public boolean process_(ISharedFolderOp op, Token tk) throws Exception
        {
            try {
                tk.inPseudoPause_(() -> {
                    final SPBlockingClient client = newMutualAuthClientFactory().create()
                            .signInRemote();
                    if (op instanceof LeaveOp){
                        client.leaveSharedFolder(BaseUtil.toPB(op.getSID()));
                    } else if (op instanceof RenameOp){
                        client.setSharedFolderName(BaseUtil.toPB(op.getSID()), ((RenameOp)op).getName());
                    } else {
                        throw new IllegalArgumentException("Unrecognized operation: " + op);
                    }
                    return null;
                });
            } catch (ExNotFound e) {
                l.info("Not a member, ignore shared folder command: {}", op);
            } catch (ExBadArgs e) {
                l.info("Root folder, ignore shared folder command: {}", op);
            }
            l.info("auto-leave.done {}", op.getSID());
            return true;
        }

        @Override
        public void dequeue_(ISharedFolderOp op, Trans t) throws SQLException
        {
            _lqdb.removeCommands_(op.getSID(), op.getType(), t);
        }
    }

    private final PersistentQueueDriver<ISharedFolderOp, ISharedFolderOp> _pqd;

    @Inject
    public SharedFolderAutoUpdater(PersistentQueueDriver.Factory f, DirectoryService ds,
            Expulsion expulsion, SharedFolderUpdateQueueDatabase lqdb)
    {
        _ds = ds;
        _lqdb = lqdb;
        _pqd = f.create(new LeaveQueue());

        _ds.addListener_(this);
        expulsion.addListener_(this);

        _pqd.scheduleScan_();
    }

    public void removeLeaveCommandsFromQueue_(SID sid, Trans t) throws SQLException
    {
        _lqdb.removeCommands_(sid, SharedFolderOpType.LEAVE, t);
    }

    private final TransLocal<Set<ISharedFolderOp>> _tlSID = new TransLocal<Set<ISharedFolderOp>>()
    {
        @Override
        protected Set<ISharedFolderOp> initialValue(Trans t)
        {
            final Set<ISharedFolderOp> ops = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    if (l.isDebugEnabled()) {
                        for (ISharedFolderOp op : ops) {
                            l.debug("auto-leave " + op);
                        }
                    }
                    _pqd.scheduleScan_();
                }
            });
            return ops;
        }
    };

    @Override
    public void objectMoved_(SOID soid, OID parentFrom, OID parentTo, Path pathFrom, Path pathTo,
            Trans t)
            throws SQLException
    {
        if (soid.oid().isAnchor() && !pathFrom.last().equals(pathTo.last())){
            l.debug("Anchor was renamed from {} to {}", pathFrom.last(), pathTo.last());
            SID sid = SID.anchorOID2storeSID(soid.oid());
            ISharedFolderOp op = new RenameOp(sid, pathTo.last());
            _pqd.enqueue_(op, t);
            _tlSID.get(t).add(op);
        }
    }

    @Override
    public void anchorExpelled_(SOID soid, Trans t) throws SQLException
    {
        if (!(soid.oid().isAnchor() && _ds.isDeleted_(_ds.getOA_(soid)))) return;

        SID sid = SID.anchorOID2storeSID(soid.oid());
        ISharedFolderOp op = new LeaveOp(sid);
        _pqd.enqueue_(op, t);
        _tlSID.get(t).add(op);
    }
}
