/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.launch_tasks;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.google.inject.Inject;
import org.slf4j.Logger;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * For stores joined before 0.4.184, the name was not stored in the DB which turns out to be an
 * issue when trying to provide a consistent UX across different storage backends. The single user
 * client can infer store names from anchors, TS w/ linked storage store name from the file system
 * but neither apporach work for TS w/ block storage...
 *
 * A name column was added to the store table and this task is used to populate it
 */
class DLTFetchStoreNames extends DaemonLaunchTask
{
    private static final Logger l = Loggers.getLogger(DLTFetchStoreNames.class);

    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final IStoreDatabase _sdb;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public DLTFetchStoreNames(TokenManager tokenManager, TransManager tm, CoreScheduler sched,
            IStoreDatabase sdb, IMapSID2SIndex sid2sidx)
    {
        super(sched);
        _tokenManager = tokenManager;
        _tm = tm;
        _sdb = sdb;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void run_() throws Exception
    {
        if (!hasStoresWithNoNames()) return;

        l.info("fetching names for stores");
        writeNamesToDatabase(getACL());
    }

    private boolean hasStoresWithNoNames() throws Exception
    {
        for (SIndex sidx : _sdb.getAll_()) if (isEmpty(_sdb.getName_(sidx))) return true;
        return false;
    }

    private GetACLReply getACL() throws Exception
    {
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "spacl4foldername");
        try {
            TCB tcb = tk.pseudoPause_("spacl4foldername");
            try {
                return newMutualAuthClientFactory().create()
                        .signInRemote()
                        .getACL(0L);
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    private void writeNamesToDatabase(GetACLReply reply)
            throws Exception
    {
        Trans t = _tm.begin_();
        try {
            for (PBStoreACL store : reply.getStoreAclList()) {
                SID sid = new SID(store.getStoreId());
                SIndex sidx = _sid2sidx.getNullable_(sid);

                if (sidx != null) {
                    _sdb.setName_(sidx, store.getName(), t);
                }
            }

            t.commit_();
        } finally {
            t.end_();
        }
    }
}
