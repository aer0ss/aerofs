/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.linked.db.LinkedStagingAreaDatabase;
import com.aerofs.daemon.core.phy.linked.db.LinkedStagingAreaDatabase.StagedFolder;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.rocklog.RockLog;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * To implement efficient atomic deletion of large object tree in LinkedStorage we introduce the
 * concept of a stagin area.
 *
 * Instead of immediately deleting files and folders, which on most systems would require walking
 * the whole tree, we simply move the folder to be deleted in a special auxiliary folder. This
 * operation is fast and has excellent transactional properties.
 *
 * Once a folder enters the staging area, it will be incrementally cleaned up. If a history path
 * was specified at the time the folder was staged, files will be moved to the appropriate location
 * in sync history, otherwise they will be discarded.
 */
public class LinkedStagingArea implements IStartable
{
    private final static Logger l = Loggers.getLogger(LinkedStagingArea.class);

    private final LinkerRootMap _lrm;
    private final LinkedStagingAreaDatabase _sadb;
    private final InjectableFile.Factory _factFile;
    private final Scheduler _sched;
    private final ExponentialRetry _er;
    private final TransManager _tm;
    private final TokenManager _tokenManager;
    private final IgnoreList _il;
    private final LinkedRevProvider _revProvider;
    private final RockLog _rl;

    private final AbstractEBSelfHandling _ev = new AbstractEBSelfHandling() {
        @Override
        public void handle_()
        {
            if (_processing) return;
            processStagedFoldersWithExponentialRetry_();
        }
    };

    // sync on core lock
    private boolean _processing;

    @Inject
    public LinkedStagingArea(LinkerRootMap lrm,
            LinkedStagingAreaDatabase sadb, InjectableFile.Factory factFile,
            CoreScheduler sched, CoreExponentialRetry cer, TransManager tm,
            TokenManager tokenManager, IgnoreList il, LinkedRevProvider revProvider,
            RockLog rl)
    {
        _lrm = lrm;
        _sadb = sadb;
        _factFile = factFile;
        _sched = sched;
        _er = cer;
        _tm = tm;
        _tokenManager = tokenManager;
        _il = il;
        _revProvider = revProvider;
        _rl = rl;
    }

    @Override
    public void start_()
    {
        _sched.schedule(_ev, 0);
    }

    /**
     * Move an existing physical folder to the staging area, optionally preserving its content
     * in sync history.
     *
     * As far as the rest of the system is concerned, this behaves like an atomic deletion of the
     * entire physical subtree. However it may take a while for all files to make their way to sync
     * history or to be cleaned up from the disk.
     */
    void stageDeletion_(String absPath, Path historyPath, Trans t)
            throws SQLException, IOException
    {
        final InjectableFile from = _factFile.create(absPath);
        if (!from.exists()) return;

        long id = _sadb.addEntry_(historyPath, t);
        final InjectableFile to = stagedPath(id, historyPath.sid());

        l.debug("staging[{}] {} -> {}", absPath, historyPath, id);

        from.moveInSameFileSystem(to);
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                if (_processing) return;
                _sched.schedule(_ev, 0);
                l.debug("scheduled processing of staging area");
            }

            @Override
            public void aborted_()
            {
                try {
                    to.moveInSameFileSystem(from);
                } catch (IOException e) {
                    l.error("db/fs inconsistent: failed to rollback move", e);
                    _rl.newDefect("linked.rollback").setException(e).send();
                }
            }
        });
    }

    /**
     * To avoid name conflicts inside the staging area, staged folders are assigned an identifier
     * obtained from a monotonically increasing counter (auto-increment key in the db).
     *
     * @param id staged folder identifier
     * @param sid physical root at time of deletion
     * @return physical path in the staging area
     */
    private InjectableFile stagedPath(long id, SID sid)
    {
        return _factFile.create(
                Util.join(_lrm.auxRoot_(sid), AuxFolder.STAGING_AREA._name, Long.toHexString(id)));
    }

    private void processStagedFoldersWithExponentialRetry_()
    {
        _er.retry("phy-staging", new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                if (!_processing) processStagedFolders_();
                return null;
            }
        });
    }

    private void processStagedFolders_() throws Exception
    {
        _processing = true;
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "psa");
        try {
            StagedFolder f;
            long lastId = -1;
            boolean ok = true;
            while ((f = nextStagedFolder_(lastId)) != null) {
                if (processStagedFolder_(f, tk)) {
                    removeEntry_(f.id);
                } else {
                    ok = false;
                }
                lastId = f.id;
            }
            if (!ok) {
                l.info("deferred processing of staging area");
                throw new ExRetryLater("staged folders remain");
            }
        } finally {
            _processing = false;
            tk.reclaim_();
        }
    }

    private @Nullable StagedFolder nextStagedFolder_(long lastId) throws SQLException
    {
        IDBIterator<StagedFolder> it = _sadb.listEntries_(lastId);
        try {
            return it.next_() ? it.get_() : null;
        } finally {
            it.close_();
        }
    }

    private void removeEntry_(long id) throws SQLException
    {
        l.debug("unstage {}", id);
        Trans t = _tm.begin_();
        try {
            _sadb.removeEntry_(id, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    private boolean processStagedFolder_(StagedFolder sf, Token tk) throws ExAborted
    {
        l.debug("staged {} {}", sf.id, sf.historyPath);
        InjectableFile f = stagedPath(sf.id, sf.historyPath.sid());
        if (!f.exists()) return true;
        TCB tcb = tk.pseudoPause_("psa");
        try {
            if (sf.historyPath.isEmpty()) {
                return f.deleteIgnoreErrorRecursively();
            } else {
                return moveToRevIgnoreErrorRecursively_(f, sf.historyPath);
            }
        } finally {
            tcb.pseudoResumed_();
        }
    }

    private boolean moveToRevIgnoreErrorRecursively_(InjectableFile f, Path p)
    {
        if (f.isFile()) {
            try {
                _revProvider.newLocalRevFile(p, f.getAbsolutePath(), KIndex.MASTER).save_();
                return true;
            } catch (IOException e) {
                return false;
            }
        } else if (f.isDirectory()) {
            String[] children = f.list();
            if (children != null) {
                for (String c : children) {
                    InjectableFile cf = _factFile.create(f, c);
                    if (_il.isIgnored(c)) {
                        cf.deleteIgnoreError();
                    } else {
                        moveToRevIgnoreErrorRecursively_(cf, p.append(c));
                    }
                }
            }
            return f.deleteIgnoreError();
        }
        l.warn("could not move to rev {} {}", f, p);
        return false;
    }
}
