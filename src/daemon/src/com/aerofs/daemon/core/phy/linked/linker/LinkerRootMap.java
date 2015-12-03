/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.FileSystemProber.ProbeException;
import com.aerofs.daemon.core.phy.linked.LinkedPath;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.BaseCfg;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableFile.Factory;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import static com.aerofs.daemon.core.notification.Notifications.newRootsChangedNotification;
import static com.aerofs.defects.Defects.newMetric;

/**
 * Maintain a mapping of SID to {@code LinkerRoot}
 */
public class LinkerRootMap
{
    private static final Logger l = Loggers.getLogger(LinkerRootMap.class);

    private final IOSUtil _os;
    private final InjectableFile.Factory _factFile;
    private final CfgAbsRoots _cfgAbsRoots;
    private final UnlinkedRootDatabase _urdb;
    private final RitualNotificationServer _rns;
    private final SharedFolderTagFileAndIcon _sfti;
    private final CfgAbsRTRoot _rtRoot;

    private LinkerRoot.Factory _factLR;
    private final Map<SID, LinkerRoot> _map = Maps.newHashMap();

    static interface IListener
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

    private Optional<IListener> _listener;

    @Inject
    public LinkerRootMap(IOSUtil os, Factory factFile, CfgAbsRoots cfgAbsRoots,
            SharedFolderTagFileAndIcon sfti, UnlinkedRootDatabase urdb, RitualNotificationServer rns,
            CfgAbsRTRoot rtRoot)
    {
        _os = os;
        _factFile = factFile;
        _urdb = urdb;
        _sfti = sfti;
        _cfgAbsRoots = cfgAbsRoots;
        _rns = rns;
        _rtRoot = rtRoot;
    }

    // work around circular dep using explicit injection of the factory
    void setFactory(LinkerRoot.Factory factLR) { _factLR = factLR; }
    void setListener_(IListener listener) { _listener = Optional.of(listener); }

    /**
     * Create new {@code LinkerRoot} for all roots specified in the conf DB.
     *
     * If the Linker throws an exception, the root may be ignored (in the case of FileNotFound)
     * or cause the daemon to exit (in all other cases).
     *
     * Returns a list of SIDs that the caller should treat as locally-unlinked.
     */
    Set<SID> init_()
    {
        Charset cs = Charset.defaultCharset();
        l.info("encoding {} {} {}", cs,
                System.getProperty("file.encoding"),
                System.getProperty("sun.jnu.encoding"));
        // NB: file system probing is more accurate at detecting broken systems so we merely
        // log encoding configuration instead of enforcing restrictions based on it alone
        if (!cs.equals(BaseUtil.CHARSET_UTF)) {
            newMetric("charset")
                    .addData("default", cs)
                    .addData("file", System.getProperty("file.encoding"))
                    .addData("jnu", System.getProperty("sun.jnu.encoding"))
                    .sendAsync();
        }

        Map<SID, String> roots;
        try {
            roots = _cfgAbsRoots.getAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        Set<SID> missingStores = Sets.newHashSet();
        for (Entry<SID, String> e : roots.entrySet()) {
            try {
                if (!add_(e.getKey(), e.getValue())) {
                    missingStores.add(e.getKey());
                }
            } catch (IOException ex) {
                l.error("failed to add root {} {} {}", e.getKey(), e.getValue(), Util.e(ex));

                setFailedRootSID_(e.getKey());
                ExitCode exitCode = (ex instanceof ProbeException)
                    ? ExitCode.FILESYSTEM_PROBE_FAILED : ExitCode.JNOTIFY_WATCH_CREATION_FAILED;
                exitCode.exit();
            }
        }
        return missingStores;
    }

    /**
     * For simplicity, use a simple text file in the rtroot to notify the UI which of possibly many
     * physical roots could not be initialized.
     */
    private void setFailedRootSID_(SID sid)
    {
        try (OutputStream o = new FileOutputStream(
                Util.join(_rtRoot.get(), LibParam.FAILED_SID))) {
            o.write(BaseUtil.string2utf(sid.toStringFormal()));
        } catch (IOException e) {
            l.warn("failed to communicate failing SID to UI", e);
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
    public void link_(final SID sid, final String absRoot, Trans t) throws IOException, SQLException
    {
        // ensure aux folders clean
        // we don't want leftover conflicts, nros or prefixes
        InjectableFile auxRoot = _factFile.create(BaseCfg.absAuxRootForPath(absRoot, sid));
        for (AuxFolder af : LibParam.AuxFolder.values()) {
            // do not enforce history cleanup as this folder could be very large
            if (af.equals(AuxFolder.HISTORY)) continue;
            _factFile.create(auxRoot, af._name).deleteOrThrowIfExistRecursively();
        }

        if (!add_(sid, absRoot)) l.warn("add root dne? {} {}", sid, absRoot);
        _sfti.addTagFileAndIconIn(sid, absRoot, t);
        _urdb.removeUnlinkedRoot(sid, t);

        // MUST be the last thing before adding the trans listener
        _cfgAbsRoots.add(sid, absRoot);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    _cfgAbsRoots.remove(sid);
                    remove_(sid);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                } catch (IOException e) {
                    // not much to do for an IOException on the compensation part of the trigger
                    l.error("ioe in rollback", e);
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

        remove_(sid);
        _sfti.removeTagFileAndIconIn(sid, absPath, t);

        // MUST be the last thing before adding the trans listener
        _cfgAbsRoots.remove(sid);

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    _cfgAbsRoots.add(sid, absPath);
                    add_(sid, absPath);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                } catch (IOException ioe) {
                    // not much to do for an IOException on the compensation part of the trigger
                    l.error("ioe in rollback", ioe);
                }
            }

            @Override
            public void committed_()
            {
                notifyRootsChanged_();
                boolean ok =_factFile.create(BaseCfg.absAuxRootForPath(absPath, sid))
                        .deleteIgnoreErrorRecursively();
                l.info("cleanup auxroot {} {}", sid, ok);
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

        move_(sid, newAbsPath);

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
        return BaseCfg.absAuxRootForPath(absRootAnchor_(root), root);
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

    /**
     * @return True if the given Store is added successfully. False if the store cannot be
     * added at the given root path (because the path does not exist, generally)
     *
     * @throws IOException Platform-specific failures adding the store will be bundled up into
     * an IO exception; for instance due to platform-specific filesystem sanity checks.
     */
    private boolean add_(SID sid, String absRoot) throws IOException
    {
        Preconditions.checkState(!_map.containsKey(sid), _map.get(sid) + " " + absRoot);
        l.info("add root {} {}", sid, absRoot);

        // must ensure existence of aux root before creating LinkerRoot as
        // filesystem properties are probed in the aux root
        // NB: can't use auxRoot_ method as the root is not in the map yet
        ensureSaneAuxRoot_(BaseCfg.absAuxRootForPath(absRoot, sid));
        LinkerRoot root = _factLR.create_(sid, absRoot);

        boolean rootExists = _factFile.create(root.absRootAnchor()).exists();

        if (rootExists) {
            _listener.get().addingRoot_(root);
        } else {
            // root folder doesn't exist. We'll remove the root in a moment. For now let's
            // try to limit how much damage this pre-unlinked root can do...
            root._removed = true;
        }
        // make sure the root is in the linker map or downstream code will get very confused.
        _map.put(root.sid(), root);

        return rootExists;
    }

    private void remove_(SID sid) throws IOException
    {
        LinkerRoot root = get_(sid);
        if (root == null) return;
        l.info("remove root {} {}", sid, root.absRootAnchor());

        _listener.get().removingRoot_(root);
        _map.remove(root.sid());
        root._removed = true;
    }

    private void move_(SID sid, String newAbsPath) throws IOException
    {
        remove_(sid);
        add_(sid, newAbsPath);
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

    void clearMap()
    {
        _map.clear();
    }
}
