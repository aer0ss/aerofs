/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.PathStatus.PBPathStatus.Flag;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Maintains an in-memory tree of paths for with status flags are set to allow efficient upwards
 * propagation of state change (instead of slow downward aggregation at lookup time).
 *
 * The flags aggregated in this structure are exactly those supported by PBPathStatus:
 *   - Uploading
 *   - Downloading
 *   - Conflict
 *
 * In the beginning all was fine and dandy but then it was decided to backtrack and not aggregate
 * the conflict status. The current solution is somewhat unpleasant to some (and extremely elegant
 * to others but that's another story) as it uses a mask to restrict upward propagation to a subset
 * of flags. This was chosen because splitting the flag data in separate classes would result in
 * a significantly more bloated design.
 */
public class PathFlagAggregator
{
    private final static Logger l = Loggers.getLogger(PathFlagAggregator.class);

    public final static int NoFlag = 0;
    public final static int Uploading   = Flag.UPLOADING_VALUE;
    public final static int Downloading = Flag.DOWNLOADING_VALUE;
    public final static int Conflict    = Flag.CONFLICT_VALUE;

    // Conflict flag is currently not propagated but changing that is as simple as OR'ing it to
    // this fancy propagation mask
    public final static int PropagationMask = Uploading | Downloading;

    private final Node _root;
    private final Map<SOCID, Path> _dlMap = Maps.newHashMap();
    private final Map<SOCID, Path> _ulMap = Maps.newHashMap();

    // for each flag, number of nodes on which it is explicitely set (as opposed to propagate from
    // some of its children)
    private final Map<Flag, Integer> _counters = Maps.newEnumMap(Flag.class);

    private static class Node
    {
        private String _name;
        private int _ownState = NoFlag;
        private int _childrenState = NoFlag;

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
                if (currentState == NoFlag && (_children == null || _children.isEmpty())) {
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
                _childrenState |= c.state() & PropagationMask;
            }
            return propagateUpwards(previousState);
        }
    }

    @Inject
    public PathFlagAggregator()
    {
        _root = new Node("", null);

        _counters.put(Flag.valueOf(Downloading), 0);
        _counters.put(Flag.valueOf(Uploading), 0);
        _counters.put(Flag.valueOf(Conflict), 0);
    }

    public Map<Path, Integer> changeFlagsOnTransferNotification_(
            SOCID socid, @Nullable Path path, TransferProgress value, int direction) {
        assert direction == Downloading || direction == Uploading : socid + " " + direction;

        /*
         * NB: the path will only be null if the SOID does not exist. There cannot be an ongoing
         * transfer unless it existed at some point in the past which means the only way to get a
         * null path is for the SOID to be deleted as a result of:
         *   - aliasing
         *   - expulsion of an entire store
         */

        l.debug("{}l: {} {} {}", (direction == Downloading ? "d" : "u"), socid,
                (path == null ? "(null)" : path), ((float)value._done / (float)value._total));

        return changeFlagsOnTransferNotification_(direction == Downloading ? _dlMap : _ulMap,
                socid, path, value, direction);
    }

    private Map<Path, Integer> changeFlagsOnTransferNotification_(
            Map<SOCID, Path> tm, SOCID socid, @Nullable Path path, TransferProgress value, int flag) {
        // list of affected path
        Map<Path, Integer> notify = Maps.newHashMap();

        if (!tm.containsKey(socid)) {
            // new transfer
            if (path != null && value._total > 0 && value._done > 0
                && value._done != value._total) {
                // only set flag when the transfer actually starts
                tm.put(socid, path);
                stateChanged_(path, flag, true, notify);
            }
        } else {
            // existing transfer
            Path previousPath = tm.get(socid);
            if (path == null || value._done == value._total) {
                // The transfer finished or the SOID was deleted: clear flag
                stateChanged_(previousPath, flag, false, notify);
                tm.remove(socid);
            } else if (!path.equals(previousPath)) {
                // The path has changed, must clear flag for previous path and set for the new one
                stateChanged_(previousPath, flag, false, notify);
                tm.put(socid, path);
                stateChanged_(path, flag, true, notify);
            }
        }

        return notify;
    }

    /**
     * Update flag tree on creation or deletion of conflict branch
     * @param path affected path
     * @param hasConflict whether the given path has existing conflict branches
     * @param notify map of state changes (out arg, to avoid cumbersome merge when batching changes)
     */
    public void changeFlagsOnConflictNotification_(Path path, boolean hasConflict,
            Map<Path, Integer> notify)
    {
        stateChanged_(path, Conflict, hasConflict, notify);
    }

    /**
     * @return the aggregate transfer state for a given {@code path}
     */
    public int state_(Path path)
    {
        Node n = node_(path, false);
        return n != null ? n.state() : NoFlag;
    }

    /**
     * @return number of nodes for which a given flag is explicitely set
     */
    public int nodesWithFlag_(int flag)
    {
        // make sure we're only testing one flag (must be a power of 2)
        assert (flag & (flag - 1)) == 0 : flag;
        return _counters.get(Flag.valueOf(flag));
    }

    /**
     * React to a state change
     * @param path Path of object whose state changed
     * @param flag flag to set/reset
     * @param set new value of the given flag
     * @param notify all state changes after upward propagation as a map(path->newState)
     */
    private void stateChanged_(Path path, int flag, boolean set, Map<Path, Integer> notify)
    {
        // make sure we're only setting flags one at a time (so flag must be a power of 2)
        assert (flag & (flag - 1)) == 0 : path + " " + flag;

        // update node and propagate state change up the object tree
        Node n = node_(path, true);
        int oldState = n._ownState;
        int d = n.setState(set ? n._ownState | flag : n._ownState & (~flag));

        // update counters
        if (n._ownState != oldState) {
            // make only one flag got changed
            assert (n._ownState ^ oldState) == flag;
            Flag f = Flag.valueOf(flag);
            _counters.put(f, _counters.get(f) + (set ? 1 : -1));
        }

        // populate notification map
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

    public boolean hasOngoingTransfers_()
    {
        return _root.state() != NoFlag ||
                (_root._children != null && !_root._children.isEmpty());
    }
}
