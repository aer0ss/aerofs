/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.RevisionInfo;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.linked.db.LinkedStagingAreaDatabase;
import com.aerofs.daemon.core.phy.linked.db.LinkedStagingAreaDatabase.StagedFolder;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.CleanupScheduler;
import com.aerofs.daemon.lib.CleanupScheduler.CleanupHandler;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.defects.Defects.newMetric;

/**
 * To implement efficient atomic deletion of large object tree in LinkedStorage we introduce the
 * concept of a staging area.
 *
 * Instead of immediately deleting files and folders, which on most systems would require walking
 * the whole tree, we simply move the folder to be deleted in a special auxiliary folder. This
 * operation is fast and has excellent transactional properties.
 *
 * Once a folder enters the staging area, it will be incrementally cleaned up. If a history path
 * was specified at the time the folder was staged, files will be moved to the appropriate location
 * in sync history, otherwise they will be discarded.
 */
public class LinkedStagingArea implements IStartable, CleanupHandler
{
    private final static Logger l = Loggers.getLogger(LinkedStagingArea.class);

    private final CleanupScheduler _sas;
    private final LinkerRootMap _lrm;
    private final LinkedStagingAreaDatabase _sadb;
    private final InjectableFile.Factory _factFile;
    private final TransManager _tm;
    private final TokenManager _tokenManager;
    private final IgnoreList _il;
    private final LinkedRevProvider _revProvider;
    private final TimeSource _ts;

    @Inject
    public LinkedStagingArea(LinkerRootMap lrm,
            LinkedStagingAreaDatabase sadb, InjectableFile.Factory factFile,
            CoreScheduler sched, TransManager tm, TimeSource ts,
            TokenManager tokenManager, IgnoreList il, LinkedRevProvider revProvider)
    {
        _sas = new CleanupScheduler(this, sched);
        _lrm = lrm;
        _sadb = sadb;
        _factFile = factFile;
        _tm = tm;
        _tokenManager = tokenManager;
        _il = il;
        _ts = ts;
        _revProvider = revProvider;
    }

    @Override
    public String name()
    {
        return "phy-staging";
    }

    @Override
    public void start_()
    {
        _sas.schedule_();
    }

    /**
     * Move an existing physical folder to the staging area, optionally preserving its content
     * in sync history.
     *
     * As far as the rest of the system is concerned, this behaves like an atomic deletion of the
     * entire physical subtree. However it may take a while for all files to make their way to sync
     * history or to be cleaned up from the disk.
     */
    @Nullable String stageDeletion_(String absPath, Path historyPath, @Nullable String rev, Trans t)
            throws SQLException, IOException {
        final InjectableFile from = _factFile.create(absPath);
        if (!from.exists()) return null;

        if (rev == null) {
            rev = new RevisionInfo(KIndex.MASTER.getInt(), _ts.getTime(), 0).hexEncoded();
        }

        // the staging area may contain entries from a previous installation
        // in which case we should:
        //   1. add a corresponding entry to the db to burn these remains
        //   2. keep looking for a "free" spot
        long id;
        InjectableFile sf;
        do {
            id = _sadb.addEntry_(historyPath, rev, t);
            sf = stagedPath(id, historyPath.sid());
        } while (sf.exists());

        l.debug("staging[{}] {} -> {}", absPath, historyPath, id);

        final InjectableFile to = sf;

        from.moveInSameFileSystem(to);
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                _sas.schedule_();
                l.debug("scheduled processing of staging area");
            }

            @Override
            public void aborted_()
            {
                try {
                    to.moveInSameFileSystem(from);
                } catch (IOException e) {
                    l.error("db/fs inconsistent: failed to rollback move", e);
                    newMetric("linked.rollback")
                            .setException(e)
                            .sendAsync();
                }
            }
        });
        return rev;
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
                Util.join(_lrm.auxRoot_(sid), ClientParam.AuxFolder.STAGING_AREA._name, Long.toHexString(id)));
    }

    boolean hasEntries_() throws SQLException {
        try (IDBIterator<?> it = _sadb.listEntries_(-1)) {
            return it.next_();
        }
    }

    @Override
    public boolean process_() throws Exception
    {
        try (Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "psa")) {
            StagedFolder f;
            long lastId = -1;
            boolean ok = true;
            while ((f = nextStagedFolder_(lastId)) != null) {
                if (processStagedFolder_(f, tk)) {
                    removeEntry_(f.id);
                } else {
                    l.info("failed to process staged folder id: {}", f.id);
                    ok = false;
                }
                lastId = f.id;
            }
            if (!ok) {
                l.info("deferred processing of staging area");
                throw new ExRetryLater("failed to clear staging area");
            }
        }
        l.info("linked staging area empty");
        return false;
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
        try (Trans t = _tm.begin_()) {
            _sadb.removeEntry_(id, t);
            t.commit_();
        }
    }

    private boolean processStagedFolder_(StagedFolder sf, Token tk) throws ExAborted
    {
        l.debug("staged {} {}", sf.id, sf.historyPath);
        // ignore entries whose physical root was unlinked
        if (_lrm.get_(sf.historyPath.sid()) == null) return true;
        InjectableFile f = stagedPath(sf.id, sf.historyPath.sid());
        if (!f.exists()) return true;
        return tk.inPseudoPause_(() -> {
            if (sf.historyPath.isEmpty()) {
                return deleteIgnoreErrorRecursively_(f);
            } else {
                return moveToRevIgnoreErrorRecursively_(f, sf);
            }
        });
    }

    private boolean deleteIgnoreErrorRecursively_(InjectableFile f)
    {
        if (f.isDirectory()) {
            fixDirectoryPermissions(f);
            InjectableFile[] children = f.listFiles();
            if (children != null) {
                for (InjectableFile child : children) deleteIgnoreErrorRecursively_(child);
            }
        }

        return f.deleteIgnoreError();
    }

    private void fixDirectoryPermissions(InjectableFile f)
    {
        try {
            f.fixDirectoryPermissions();
        } catch (Exception e) {
            l.warn("couldn't set write permissions on folder at {} in the staging area," +
                    "check that the daemon owns those folders", f, BaseLogUtil.suppress(e));
        }
    }

    private boolean moveToRevIgnoreErrorRecursively_(InjectableFile f, StagedFolder sf)
    {
        if (!f.exists()) {
            // file doesn't exist, nothing we need to delete
            return true;
        } else if (f.isDirectory()) {
            fixDirectoryPermissions(f);
            String[] children = f.list();
            if (children != null) {
                for (String c : children) {
                    InjectableFile cf = _factFile.create(f, c);
                    if (_il.isIgnored(c)) {
                        deleteIgnoreErrorRecursively_(cf);
                    } else {
                        moveToRevIgnoreErrorRecursively_(cf,
                                new StagedFolder(-1, sf.historyPath.append(c), sf.rev));
                    }
                }
            }
            return f.deleteIgnoreError();
        } else if (f.isFile() || f.isSymbolicLink()) {
            // second condition is for dangling symlinks
            try {
                if (_il.isIgnored(f.getName())) {
                    return f.deleteIgnoreError();
                } else if (sf.rev != null) {
                    _revProvider.localRevFile(sf.historyPath, f.getAbsolutePath(), sf.rev).save_();
                } else {
                    _revProvider.newLocalRevFile(sf.historyPath, f.getAbsolutePath(), KIndex.MASTER).save_();
                }
                return true;
            } catch (IOException e) {
                l.warn("could not move to rev {} {}", f, sf, BaseLogUtil.suppress(e));
                return false;
            }
        }
        l.warn("could not move to rev {} {}", f, sf);
        return false;
    }
}
