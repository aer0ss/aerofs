/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.slf4j.Logger;

class LinkRootUtil
{
    private static final Logger l = Loggers.getLogger(LinkRootUtil.class);

    private final StoreCreator _sc;
    private final TransManager _tm;
    private final LinkerRootMap _lrm;
    private final CfgAbsRTRoot _cfgAbsRTRoot;

    @Inject
    public LinkRootUtil(TransManager tm, StoreCreator sc,LinkerRootMap lrm,
            CfgAbsRTRoot cfgAbsRTRoot)
    {
        _tm = tm;
        _sc = sc;
        _lrm = lrm;
        _cfgAbsRTRoot = cfgAbsRTRoot;
    }

    void checkSanity(InjectableFile f) throws Exception
    {
        if (!f.exists()) throw new ExNotFound();
        if (!f.isDirectory()) throw new ExNotDir();

        // check for conflict with existing roots
        String canonicalPath = f.getCanonicalPath();
        l.info("canon {}", canonicalPath);
        if (_lrm.isAnyRootUnder_(canonicalPath)) {
            throw new ExChildAlreadyShared();
        }
        if (_lrm.rootForAbsPath_(canonicalPath) != null) {
            throw new ExParentAlreadyShared();
        }

        RootAnchorUtil.checkRootAnchor(f.getAbsolutePath(), _cfgAbsRTRoot.get(), StorageType.LINKED,
                true);
    }

    SIndex linkRoot(InjectableFile f, SID sid) throws Exception
    {
        try (Trans t = _tm.begin_()) {
            // If creating store, must link before creating store. This prevents creation of a
            // conflicting root in TS (FlatLinkedStorage creates new root by default on every
            // store creation)
            _lrm.link_(sid, f.getAbsolutePath(), t);

            SIndex sidx = _sc.createRootStore_(sid, f.getName(), t);
            t.commit_();
            return sidx;
        }
    }
}