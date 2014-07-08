/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryServiceAdapter;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Keep track of all objects for which conflict branches have been added/removed
 * during a transaction and emit a notification when the transaction is committed.
 */
class ConflictNotifier extends DirectoryServiceAdapter
{
    private final DirectoryService _ds;
    private final List<IConflictStateListener> _listeners = Lists.newArrayList();

    /**
     * For each ongoing transaction, build the set of objects for which conflict branches have
     * changed. It will be passed to listeners when the transaction is committed.
     *
     * Because file names can sometimes change during a transaction (e.g. deletion) we also keep
     * track of the initial name to make sure listeners can properly clear any cached data.
     */
    private final TransLocal<Map<SOID, Path>> _tlConflictedObjects =
            new TransLocal<Map<SOID, Path>>() {
        @Override
        protected Map<SOID, Path> initialValue(Trans t)
        {
            final Map<SOID, Path> map = Maps.newHashMap();
            t.addListener_(new AbstractTransListener() {
                private Map<Path, Boolean> conflicts = Maps.newHashMap();
                @Override
                public void committing_(Trans t) throws SQLException
                {
                    if (!map.isEmpty()) {
                        for (Entry<SOID, Path> e : map.entrySet()) {
                            // if the file was deleted we still want to send a notification to avoid
                            // keeping stale data in caches of RitualNotification clients
                            OA oa = _ds.getOANullable_(e.getKey());
                            if (oa != null) conflicts.put(e.getValue(), oa.cas().size() > 1);
                        }
                    }
                }

                @Override
                public void committed_()
                {
                    if (!conflicts.isEmpty()) {
                        for (IConflictStateListener l : _listeners) l.branchesChanged_(conflicts);
                    }
                }
            });

            return map;
        }
    };

    public static interface IConflictStateListener
    {
        /**
         * Called when a transaction that adds or removes conflict branches is committed.
         * @param paths Set of path for which conflict branches have been added and/or removed
         * NOTE: always called from a thread currently holding the core lock
         */
        void branchesChanged_(Map<Path, Boolean> paths);
    }

    @Inject
    public ConflictNotifier(DirectoryService ds)
    {
        _ds = ds;
        _ds.addListener_(this);
    }

    public void addListener_(IConflictStateListener listener)
    {
        _listeners.add(listener);
    }

    /**
     * Send a snapshot of the current conflict state to a listener
     */
    public void sendSnapshot_(IConflictStateListener listener) throws SQLException
    {
        Map<Path, Boolean> paths = Maps.newHashMap();
        IDBIterator<SOKID> it = _ds.getAllNonMasterBranches_();
        try {
            while (it.next_()) {
                SOID soid = it.get_().soid();
                Path path = _ds.resolveNullable_(soid);
                if (path != null) paths.put(path, _ds.getOA_(soid).cas().size() > 1);
            }
        } finally {
            it.close_();
        }
        listener.branchesChanged_(paths);
    }

    @Override
    public void objectDeleted_(SOID obj, OID parent, Path pathFrom, Trans t) throws SQLException
    {
        OA oa = _ds.getOANullable_(obj);
        // Here be dragons: we must use casNoExpulsionCheck() because by the time this method
        // is called the object is expelled and cas() would always return an empty map.
        // This entire approach to conflict couting and notification is broken (for instance
        // the conflict count will lag behind as a result of scalable deletion). There are
        // ways to address this and a task has been created (ENG-1814) but it is not deemed
        // high-prio at this time.
        if (oa != null && oa.isFile() && oa.casNoExpulsionCheck().size() > 1) {
            // when deleting an object with conflict branches, the name will be changed before
            // the conflict branches are deleted, so we need to set the path in the translocal
            // map first to avoid sending notifications for bogus path (i.e. inside the trash)
            add(obj, pathFrom, t);
        }
    }

    private void add(SOID soid, Path path, Trans t)
    {
        Map<SOID, Path> m = _tlConflictedObjects.get(t);
        // only set the path the first time, that way if the object is being deleted we will send
        // notifications for the old path and not somewhere inside the trash
        if (!m.containsKey(soid)) m.put(soid, path);
    }

    @Override
    public void objectContentCreated_(SOKID sokid, Path path, Trans t) throws SQLException
    {
        if (!sokid.kidx().isMaster()) add(sokid.soid(), path, t);
    }

    @Override
    public void objectContentDeleted_(SOKID sokid, Trans t) throws SQLException
    {
        if (!sokid.kidx().isMaster()) {
            Path path = _ds.resolveNullable_(sokid.soid());
            if (path != null) add(sokid.soid(), path, t);
        }
    }

    @Override
    public void objectContentModified_(SOKID sokid, Path path, Trans t) throws SQLException
    {
        if (!sokid.kidx().isMaster()) add(sokid.soid(), path, t);
    }

    @Override
    public void objectObliterated_(OA oa, Trans t) throws SQLException
    {
        // do not send notifications for temporary objects created by aliasing
        _tlConflictedObjects.get(t).remove(oa.soid());
    }
}
