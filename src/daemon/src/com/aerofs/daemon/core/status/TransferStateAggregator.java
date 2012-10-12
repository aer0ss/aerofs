/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.IDownloadStateListener.Ended;
import com.aerofs.daemon.core.net.IDownloadStateListener.Ongoing;
import com.aerofs.daemon.core.net.IDownloadStateListener.State;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;

/**
 * Maintains an in-memory tree of paths involved in upload/download to allow efficient upwards
 * propagation of state change (instead of slow downward aggregation at lookup time).
 */
public class TransferStateAggregator
{
    private final static Logger l = Util.l(TransferStateAggregator.class);

    public final static int NoTransfer  = 0;
    public final static int Uploading   = PBPathStatus.Flag.UPLOADING_VALUE;
    public final static int Downloading = PBPathStatus.Flag.DOWNLOADING_VALUE;

    private final Node _root;
    private final Map<SOCID, Path> _dlMap = Maps.newHashMap();
    private final Map<SOCID, Path> _ulMap = Maps.newHashMap();

    private final DirectoryService _ds;

    private static class Node
    {
        private String _name;
        private int _ownState;
        private int _childrenState;

        private Node _parent;
        private Map<String, Node> _children;

        Node(String n, Node p)
        {
            _name = n;
            _parent = p;
        }

        int state()
        {
            return _ownState | _childrenState;
        }

        /**
         * Update state and propagate changes upwards if needed
         * @param state new state
         * @return number of nodes affected by upwards change (including this node)
         */
        int setState(int state) {
            // update own state
            int previousState = _ownState | _childrenState;
            _ownState = state;
            return propagateUpwards(previousState);
        }

        private int propagateUpwards(int previousState)
        {
            int currentState = _ownState | _childrenState;
            if (currentState == previousState) return 0;
            // propagate state change upwards if needed
            if (_parent != null) {
                // auto-removal of nodes who no longer carry any useful state
                if (currentState == NoTransfer && (_children == null || _children.isEmpty())) {
                    _parent._children.remove(_name);
                }
                return 1 + _parent.updateChildrenStatus();
            }
            return 1;
        }

        private int updateChildrenStatus() {
            int previousState = _ownState | _childrenState;
            _childrenState = 0;
            for (Node c : _children.values()) {
                _childrenState |= c.state();
            }
            return propagateUpwards(previousState);
        }
    }

    @Inject
    public TransferStateAggregator(DirectoryService ds)
    {
        _ds = ds;
        _root = new Node("", null);
    }

    /**
     * Update transfer state tree on download listener callback
     * @return actual state changes requiring notification
     */
    public Map<Path, Integer> download_(SOCID socid, State state) {
        // list of affected path
        Map<Path, Integer> notify = Maps.newHashMap();

        Path path;
        try {
            path = _ds.resolveNullable_(socid.soid());
        } catch (SQLException e) {
            l.warn(Util.e(e));
            return notify;
        }
        if (path == null) return notify;

        if (!_dlMap.containsKey(socid)) {
            if (state instanceof Ongoing) {
                // This is a new download
                _dlMap.put(socid, path);
                stateChanged_(path, Downloading, true, notify);
            }
        } else {
            Path previousPath = _dlMap.get(socid);
            if (previousPath != null && !path.equals(previousPath)) {
                // The path has changed, must clear the Downloading flag for the previous path
                stateChanged_(previousPath, Downloading, false, notify);
                _dlMap.put(socid, path);
                stateChanged_(path, Downloading, true, notify);
            }
            if (state instanceof Ended) {
                stateChanged_(path, Downloading, false, notify);
                _dlMap.remove(socid);
            }
        }

        return notify;
    }

    /**
     * Update transfer state tree on upload listener callback
     * @return actual state changes requiring notification
     */
    public Map<Path, Integer> upload_(SOCID socid, Value value) {
        // list of affected path
        Map<Path, Integer> notify = Maps.newHashMap();

        Path path;
        try {
            path = _ds.resolveNullable_(socid.soid());
        } catch (SQLException e) {
            l.warn(Util.e(e));
            return notify;
        }
        if (path == null) return notify;

        if (!_ulMap.containsKey(socid)) {
            if (value._total > 0 && value._done > 0 && value._done != value._total) {
                // This is a new upload
                _ulMap.put(socid, path);
                stateChanged_(path, Uploading, true, notify);
            }
        } else {
            Path previousPath = _ulMap.get(socid);
            if (previousPath != null && !path.equals(previousPath)) {
                // The path has changed, must clear the Uploading flag for the previous path
                stateChanged_(previousPath, Uploading, false, notify);
                _ulMap.put(socid, path);
                stateChanged_(path, Uploading, true, notify);
            }
            if (value._done == value._total) {
                // The upload finished
                stateChanged_(path, Uploading, false, notify);
                _ulMap.remove(socid);
            }
        }

        return notify;
    }

    /**
     * @return the aggregate transfer state for a given {@code path}
     */
    public int state_(Path path)
    {
        Node n = node_(path, false);
        return n != null ? n.state() : NoTransfer;
    }

    /**
     * React to a state change
     * @param path Path of object whose state changed
     * @param state transfer type (Uploading or Downloading)
     * @param start whether the state change is the start of a transfer
     * @param notify all state changes after upward propagation as a map(path->newState)
     */
    private void stateChanged_(Path path, int state, boolean start, Map<Path, Integer> notify)
    {
        Node n = node_(path, true);
        int d = n.setState(start ? n._ownState | state : n._ownState & (~state));
        while (d > 0) {
            notify.put(path, n.state());
            // break now to avoid an assert failure in Path in case we reached the root
            if (--d == 0) break;
            path = path.removeLast();
            n = n._parent;
        }
    }

    /**
     * Find node corresponding to a given path in the transfer state tree
     * @param create if true, missing nodes will be created
     * @return the node corresponding to {@code path}, null if not found and {@code create==false}
     */
    private @Nullable Node node_(Path path, boolean create)
    {
        Node n = _root;
        for (String s : path.elements()) {
            if (n._children == null) {
                if (create) {
                    n._children = Maps.newHashMap();
                } else {
                    return null;
                }
            }
            Node c = n._children.get(s);
            if (c == null) {
                if (create) {
                    c = new Node(s, n);
                    n._children.put(s, c);
                } else {
                    return null;
                }
            }
            n = c;
        }
        return n;
    }
}
