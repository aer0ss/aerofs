/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Sp.GetSharedFolderNamesReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import java.util.List;

/**
 * For stores joined before 0.4.184, the name was not stored in the DB which turns out to be an
 * issue when trying to provide a consistent UX across different storage backends. Tthe single user
 * client can infer store names from anchors, TS w/ linked storage store name from the file system
 * but neither apporach work for TS w/ block storage...
 *
 * A name column was added to the store table and this task is used to populate it
 */
class RALOFetchStoreNames extends RunAtLeastOnce
{
    private static final Logger l = Loggers.getLogger(RALOFetchStoreNames.class);

    private final TC _tc;
    private final TransManager _tm;
    private final IStoreDatabase _sdb;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public RALOFetchStoreNames(TC tc, TransManager tm, CoreScheduler sched, IStoreDatabase sdb,
            IMapSIndex2SID sidx2sid)
    {
        super(sched);
        _tc = tc;
        _tm = tm;
        _sdb = sdb;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void run_() throws Exception
    {
        // find out which stores are still unnamed
        List<SIndex> stores = Lists.newArrayList();
        List<ByteString> sids = Lists.newArrayList();
        for (SIndex sidx : _sdb.getAll_()) {
            if (_sdb.getName_(sidx) == null) {
                stores.add(sidx);
                sids.add(_sidx2sid.get_(sidx).toPB());
            }
        }

        if (stores.isEmpty()) return;

        l.info("fetching names for {} stores", stores.size());

        // query sp for missing names
        GetSharedFolderNamesReply reply;
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spfoldername");
        try {
            TCB tcb = tk.pseudoPause_("spfoldername");
            try {
                SPBlockingClient sp = new SPBlockingClient.Factory().create_(Cfg.user());
                sp.signInRemote();
                reply = sp.getSharedFolderNames(sids);
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }

        if (stores.size() != reply.getFolderNameCount()) throw new ExProtocolError();

        // write names to db
        Trans t = _tm.begin_();
        try {
            for (int i = 0; i < stores.size(); ++i) {
                l.info("store {} {}", stores.get(i), reply.getFolderName(i));
                _sdb.setName_(stores.get(i), reply.getFolderName(i));
            }
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
