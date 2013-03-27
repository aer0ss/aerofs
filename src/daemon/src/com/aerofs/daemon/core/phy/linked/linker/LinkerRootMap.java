/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;
import sun.tools.tree.IfStatement;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Maintain a mapping of SID to {@code LinkerRoot}
 */
public class LinkerRootMap
{
    private static final Logger l = Loggers.getLogger(LinkerRootMap.class);

    private final CfgAbsRoots _cfgAbsRoots;

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
    public LinkerRootMap(CfgAbsRoots cfgAbsRoots)
    {
        _cfgAbsRoots = cfgAbsRoots;
    }

    // work around circular dep using explicit injection of the factory
    void setFactory(LinkerRoot.Factory factLR)
    {
        _factLR = factLR;
    }

    void addListener_(IListener listener)
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
        try {
            for (Entry<SID, String> e : _cfgAbsRoots.get().entrySet()) {
                IOException ex = add_(e.getKey(), e.getValue());
                if (ex != null) {
                    l.warn("failed to add root {} {} {}", e.getKey(), e.getValue(), Util.e(ex));
                }
            }
        } catch (SQLException e) {
            SystemUtil.fatal(e);
        }
    }

    /**
     * @return the {@code LinkerRoot} under which the path resides
     */
    public @Nullable SID rootForAbsPath_(String absPath)
    {
        // TODO: use Map<String, LinkerRoot> (what of case-sensitivity then?)
        for (LinkerRoot root : getAllRoots_()) {
            if (Path.isUnder(root.absRootAnchor(), absPath)) return root.sid();
        }
        return null;
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

        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    _cfgAbsRoots.remove(sid);
                } catch (SQLException e) {
                    SystemUtil.fatal(e);
                }
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
        });
    }

    public @Nullable String absRootAnchor_(SID sid)
    {
        LinkerRoot root = _map.get(sid);
        return root != null ? root.absRootAnchor() : null;
    }

    Collection<LinkerRoot> getAllRoots_()
    {
        return _map.values();
    }

    private LinkerRoot get_(SID sid)
    {
        return _map.get(sid);
    }

    private IOException add_(SID sid, String absRoot)
    {
        assert !_map.containsKey(sid) : _map.get(sid) + " " + absRoot;
        l.info("add root {} {}", sid, absRoot);

        try {
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
        try {
            for (IListener listener : _listeners) listener.removingRoot_(root);
            _map.remove(root.sid());
            root._removed = true;
        } catch (IOException e) {
            return e;
        }
        return null;
    }
}
