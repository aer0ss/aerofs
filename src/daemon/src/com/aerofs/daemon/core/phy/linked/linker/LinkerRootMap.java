/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.phy.linked.LinkedPath;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.PendingRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.daemon.core.notification.Notifications.newRootsChangedNotification;

/**
 * Maintain a mapping of SID to {@code LinkerRoot}
 */
public class LinkerRootMap
{
    private static final Logger l = Loggers.getLogger(LinkerRootMap.class);

    private final IOSUtil _os;
    private final InjectableFile.Factory _factFile;
    private final CfgAbsRoots _cfgAbsRoots;
    private final PendingRootDatabase _prdb;
    private final RitualNotificationServer _rns;

    private LinkerRoot.Factory _factLR;
    private final Map<SID, LinkerRoot> _map = Maps.newHashMap();

    public static interface IListener
    {
        /**
         * Called *before* the root is effectively added
         */
        void addingRoot_(LinkerRoot root) throws IOException;

        /**
         * Called *before* the root is effectively removed
         */
        void removingRoot_(LinkerRoot root) throws IOException;
    }

    private List<IListener> _listeners = Lists.newArrayList();

    @Inject
    public LinkerRootMap(IOSUtil os, InjectableFile.Factory factFile, CfgAbsRoots cfgAbsRoots,
            PendingRootDatabase prdb, RitualNotificationServer rns)
    {
        _os = os;
        _factFile = factFile;
        _prdb = prdb;
        _cfgAbsRoots = cfgAbsRoots;
        _rns = rns;
    }

    // work around circular dep using explicit injection of the factory
    void setFactory(LinkerRoot.Factory factLR)
    {
        _factLR = factLR;
    }

    public void addListener_(IListener listener)
    {
        _listeners.add(listener);
    }

    /**
     * Create new {@code LinkerRoot} for all roots specified in the conf DB
     *
     * NB: roots for which the listener throws an exception are ignored
     */
    void init_()
    {
        Map<SID, String> roots;
        try {
            roots = _cfgAbsRoots.get();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        for (Entry<SID, String> e : roots.entrySet()) {
            IOException ex = add_(e.getKey(), e.getValue());
            if (ex != null) {
                l.error("failed to add root {} {} {}", e.getKey(), e.getValue(), Util.e(ex));
                ExitCode.JNOTIFY_WATCH_CREATION_FAILED.exit();
            }
        }
    }

    /**
     * @return the {@code LinkerRoot} under which the path resides
     */
    public @Nullable SID rootForAbsPath_(String absPath)
    {
        // TODO: use a more efficient O(log n) algorithm...
        for (LinkerRoot root : getAllRoots_()) {
            if (Path.isUnder(root.absRootAnchor(), absPath)) return root.sid();
        }
        return null;
    }

    /**
     * @return the {@code LinkerRoot} under which the path resides
     */
    public boolean isAnyRootUnder_(String absPath)
    {
        // TODO: use a more efficient O(log n) algorithm...
        for (LinkerRoot root : getAllRoots_()) {
            if (Path.isUnder(absPath, root.absRootAnchor())) return true;
        }
        return false;
    }

    private void notifyRootsChanged_()
    {
        _rns.getRitualNotifier().sendNotification(newRootsChangedNotification());
    }

    /**
     * Create a link between a physical path and a SID
     *
     * Update conf DB and create a new {@code LinkerRoot}
     */
    public void link_(final SID sid, String absRoot, Trans t) throws IOException, SQLException
    {
        IOException e = add_(sid, absRoot);
        if (e != null) throw e;
        _cfgAbsRoots.add(sid, absRoot);
        _prdb.removePendingRoot(sid, t);

        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void aborted_()
            {
                try {
                    _cfgAbsRoots.remove(sid);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                }
            }

            @Override
            public void committed_()
            {
                notifyRootsChanged_();
            }
        });
    }

    /**
     * Break an existing link between a physical path and a SID
     *
     * Update conf DB and cleanup any existing {@code LinkerRoot}
     */
    public void unlink_(final SID sid, Trans t) throws IOException, SQLException
    {
        final String absPath = absRootAnchor_(sid);
        if (absPath == null) return;

        IOException e = remove_(sid);
        if (e != null) throw e;
        _cfgAbsRoots.remove(sid);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    _cfgAbsRoots.add(sid, absPath);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                }
            }

            @Override
            public void committed_()
            {
                notifyRootsChanged_();
            }
        });
    }

    /**
     * Point an existing link between a physical path and an SID to a new physical path
     */
    private void relink_(final SID sid, String newAbsPath, Trans t) throws IOException, SQLException
    {
        final String absPath = absRootAnchor_(sid);
        assert absPath != null : sid;

        IOException e = move_(sid, newAbsPath);
        if (e != null) throw e;

        _cfgAbsRoots.move(sid, newAbsPath);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    _cfgAbsRoots.move(sid, absPath);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                }
            }

            @Override
            public void committed_()
            {
                notifyRootsChanged_();
            }
        });
    }

    /**
     * Point an existing link between a physical path and an SID to a new physical path
     *
     * NB: will correctly handle relocation of the default root anchor on TeamServer
     */
    public void move_(SID sid, String oldAbsPath, String newAbsPath, Trans t)
            throws IOException, SQLException
    {
        if (sid.equals(Cfg.rootSID()) && L.isMultiuser()) {
            // special case: on TeamServer the defaultAbsRoot itself is not associated with a
            // LinkerRoot (as the rootSID is unused) but instead it contains user homes and shared
            // folders, each being associated with its own LinkerRoot
            // For consistency and ease of use, we support relocating the defaultAbsRoot and when
            // that happens we need to transparently relocate all the implicit roots under it

            Map<SID, String> newRoots = Maps.newHashMap();
            for (Entry<SID, LinkerRoot> e : _map.entrySet()) {
                String rootPath = e.getValue().absRootAnchor();
                if (Path.isUnder(oldAbsPath, rootPath)) {
                    String rootRelPath = Path.relativePath(oldAbsPath, rootPath);
                    newRoots.put(e.getKey(), Util.join(newAbsPath, rootRelPath));
                }
            }

            for (Entry<SID, String> e : newRoots.entrySet()) {
                relink_(e.getKey(), e.getValue(), t);
            }
        } else if (_map.containsKey(sid)) {
            relink_(sid, newAbsPath, t);
        }
    }

    public @Nullable String absRootAnchor_(SID sid)
    {
        LinkerRoot root = get_(sid);
        return root != null ? root.absRootAnchor() : null;
    }

    public final String auxRoot_(SID root)
    {
        return Cfg.absAuxRootForPath(absRootAnchor_(root), root);
    }

    public final String auxFilePath_(SID sid, SOID soid, AuxFolder folder)
    {
        return Util.join(auxRoot_(sid), folder._name, LinkedPath.makeAuxFileName(soid));
    }

    public final String auxFilePath_(SID sid, SOKID sokid, AuxFolder folder)
    {
        return Util.join(auxRoot_(sid), folder._name, LinkedPath.makeAuxFileName(sokid));
    }

    /**
     * @pre both Path must be non-empty
     * @return whether two Path are physically equivalent
     *
     * physical equivalence is defined by
     *   * equality of logical parent
     *   * filesystem-specific equivalence check of the last path component
     */
    public boolean isPhysicallyEquivalent_(Path a, Path b)
    {
        Preconditions.checkArgument(!a.isEmpty());
        Preconditions.checkArgument(!b.isEmpty());
        return a.removeLast().equals(b.removeLast())
                && get_(a.sid()).isPhysicallyEquivalent(a.last(), b.last());
    }

    public Collection<LinkerRoot> getAllRoots_()
    {
        return _map.values();
    }

    public LinkerRoot get_(SID sid)
    {
        return _map.get(sid);
    }

    private IOException add_(SID sid, String absRoot)
    {
        Preconditions.checkState(!_map.containsKey(sid), _map.get(sid) + " " + absRoot);
        l.info("add root {} {}", sid, absRoot);

        try {
            // must ensure existence of aux root before creating LinkerRoot as
            // filesystem properties are probed in the aux root
            // NB: can't use auxRoot_ method as the root is not in the map yet
            ensureSaneAuxRoot_(Cfg.absAuxRootForPath(absRoot, sid));
            LinkerRoot root = _factLR.create_(sid, absRoot);
            for (IListener listener : _listeners) listener.addingRoot_(root);
            _map.put(root.sid(), root);
        } catch (IOException e) {
            return e;
        }
        return null;
    }

    private IOException remove_(SID sid)
    {
        LinkerRoot root = get_(sid);
        if (root == null) return null;
        l.info("remove root {} {}", sid, root.absRootAnchor());

        try {
            for (IListener listener : _listeners) listener.removingRoot_(root);
            _map.remove(root.sid());
            root._removed = true;
        } catch (IOException e) {
            return e;
        }
        return null;
    }

    private IOException move_(SID sid, String newAbsPath)
    {
        IOException e = remove_(sid);
        return e != null ? e : add_(sid, newAbsPath);
    }

    private void ensureSaneAuxRoot_(String absAuxRoot) throws IOException
    {
        l.info("aux root {}", absAuxRoot);

        // create aux folders. other codes assume these folders already exist.
        for (AuxFolder af : LibParam.AuxFolder.values()) {
            _factFile.create(Util.join(absAuxRoot, af._name)).ensureDirExists();
        }

        _os.markHiddenSystemFile(absAuxRoot);
    }
}
