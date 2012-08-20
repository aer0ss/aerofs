/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.dependence;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.ReceiveAndApplyUpdate;
import com.aerofs.daemon.core.net.ReceiveAndApplyUpdate.CausalityResult;
import com.aerofs.daemon.core.net.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.core.net.proto.MetaDiff;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableList;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ListIterator;

/**
 * Given a download dependency cycle (deadlock), the DownloadDeadlockResolver classifies the type of
 * cycle and attempts to resolve the deadlock.  If the deadlock is unrecognized, this class crashes
 * the app which will notify the development team of this unforeseen deadlock.
 */
public class DownloadDeadlockResolver
{
    private static final Logger l = Util.l(DownloadDeadlockResolver.class);

    private final TransManager _tm;
    private final DirectoryService _ds;
    private final ReceiveAndApplyUpdate _ru;
    private final MetaDiff _mdiff;

    @Inject
    public DownloadDeadlockResolver(DirectoryService ds, TransManager tm, ReceiveAndApplyUpdate ru,
            MetaDiff mdiff)
    {
        _ds = ds;
        _tm = tm;
        _ru = ru;
        _mdiff = mdiff;
    }

    public void resolveDeadlock_(final ImmutableList<DependencyEdge> cycle)
            throws Exception
    {
        // Cycles that are currently unsupported, those with:
        // * length less than two (a cycle of 1 is impossible)
        // * any UNSPECIFIED dependency type
        // * more than two NAME_CONFLICTs
        assert cycle.size() > 1;
        int countNameConflicts = 0;
        for (DependencyEdge dependency : cycle) {
            DependencyType type = dependency.type();
            if (type.equals(DependencyType.NAME_CONFLICT)) countNameConflicts++;

            assert !type.equals(DependencyType.UNSPECIFIED) : cycle;
        }
        assert countNameConflicts <= 2 : cycle;

        // Case 1: cycle of 2 elements, both are NAME_CONFLICTS
        NameConflictDependencyEdge ncDependency = detectBidirectionalNameConflict(cycle);
        if (ncDependency != null) {
            // resolve by renaming the local conflict OID
            breakDependencyByRenaming_(ncDependency);
            return;
        }

        // Case 2: chain of parental dependencies with one name conflict
        ncDependency = detectAncestralNameConflict(cycle);
        if (ncDependency != null) {
            // resolve by renaming the local child OID.
            breakDependencyByRenaming_(ncDependency);
            return;
        }

        // If execution reaches this point, the two prior cases don't apply for this cycle
        assert false: cycle;
    }

    /**
     * Deadlock case. Two devices A, B. Two OIDs o1, o2. Two names n1, n2. Observe:
     *       A      B
     *      n1o1   n2o1
     *      n2o2   n1o2
     * @return the NameConflictDependencyEdge to resolve by renaming the local OID
     */
    private static @Nullable
    NameConflictDependencyEdge detectBidirectionalNameConflict(
            final ImmutableList<DependencyEdge> cycle)
    {
        // Check the applicability of this case:
        // 1) only bidirectional deadlock (for now)
        // 2) both dependencies must be NAME_CONFLICT

        if (cycle.size() != 2) return null;
        for (DependencyEdge dep : cycle) {
            if (!dep.type().equals(DependencyType.NAME_CONFLICT)) return null;
        }

        // Since all dependencies in the cycle are NAME_CONFLICT, then every SOCID in the cycle
        // must be present locally, therefore we arbitrarily rename the first of those SOCIDs to
        // break the cycle.
        return NameConflictDependencyEdge.class.cast(cycle.get(0));
    }

    /**
     * Detect
     * Deadlock case: remote _parent OID has name conflict with local to-be child OID
     * For an example, see the SyncDET test
     *   core.aliasing.should_move_folder_under_name_conflicted_parent
     * 1) find a "name conflict" dependency---the dependee is local, the dependent is remote
     * 2) determine if the local oid is a descendent (child/grandchild) of the remote oid
     * @return the NameConflictDependencyEdge to resolve by renaming the local OID
     */
    private static @Nullable
    NameConflictDependencyEdge detectAncestralNameConflict(
            final ImmutableList<DependencyEdge> cycle)
    {
        NameConflictDependencyEdge ncRet = null;
        ListIterator<DependencyEdge> iter = cycle.listIterator();
        while (iter.hasNext()) {
            DependencyEdge dependency = iter.next();
            if (dependency.type().equals(DependencyType.NAME_CONFLICT)) {
                ncRet = NameConflictDependencyEdge.class.cast(dependency);
                break;
            }
        }

        if (ncRet == null) {
            l.info("no name conflict found");
            return null;
        }

        // Label the local and remote socids for easier reading
        SOCID socidLocalChild = ncRet.dst;
        SOCID socidRemoteAncestor = ncRet._src;

        // Assume the src of the next dependency is the socidLocalChild
        // (taking list wrapping into account)
        if (iter.hasNext()) {
            if (!socidLocalChild.equals(iter.next()._src)) {
                return null;
            }
            iter.previous();
        } else if (!socidLocalChild.equals(cycle.get(0)._src)) {
            return null;
        }

        // Assume the dst of the previous dependency is the socidRemoteAncestor
        iter.previous();
        if (iter.hasPrevious()) {
            if (!socidRemoteAncestor.equals(iter.previous().dst)) {
                return null;
            }
        } else if (!socidRemoteAncestor.equals(cycle.get(cycle.size() - 1).dst)) {
            return null;
        }

        assert cycle.size() > 1 : cycle;

        // Verify that the rest of the path follows a correct ancestral chain:
        DependencyEdge prev = cycle.get(cycle.size() - 1);
        for (DependencyEdge cur : cycle) {
            assert prev.dst.equals(cur._src) : "prev " + prev + " cur " + cur;
            if (cur.type().equals(DependencyType.PARENT)) {
                // Common case, no-op
            } else {
                // This dependency is not a PARENT type, so the Ancestral Name Conflict case does
                // not apply any of the following are not true
                // * the current dependency is a NAME_CONFLICT
                // * the destination does not match the SOCID of the expected local child
                // * the source does not match the SOCID of the expected remote ancestor
                if (!(socidLocalChild.equals(cur.dst) && socidRemoteAncestor.equals(cur._src)
                        && cur.type().equals(DependencyType.NAME_CONFLICT))) {
                    l.info("bad chain");
                    return null;
                }
            }
            prev = cur;
        }

        return ncRet;
    }

    /**
     * Break the dependency by renaming the source SOCID of the name-conflict
     */
    private void breakDependencyByRenaming_(@Nonnull NameConflictDependencyEdge dependency)
            throws Exception
    {
        // The dependee SOCID should be local, the dependent should be remote;
        // the local will be renamed
        SOCID socidLocal = dependency.dst;
        SOCID socidRemote = dependency._src;
        Version vRemote = dependency._vSrc;

        // A name conflict should only involve META socids
        assert socidRemote.cid().isMeta() && socidRemote.cid().equals(socidLocal.cid())
                : dependency;

        Path pParent = _ds.resolve_(new SOID(socidLocal.sidx(), dependency._parent));
        int metaDiff = _mdiff.computeMetaDiff_(socidRemote.soid(), dependency._meta,
                dependency._parent);

        SOCKID sockidRemote = new SOCKID(socidRemote);
        boolean wasPresent = _ds.isPresent_(sockidRemote);

        // TODO (MJ) what if the socidLocal changed while we were processing all of the received
        // components? We should probably abort and start the download anew.

        Trans t = _tm.begin_();
        try {
            CausalityResult cr = _ru.computeCausalityForMeta_(socidRemote.soid(), vRemote,
                    metaDiff);
            assert cr != null : socidRemote + " " + vRemote + " " + metaDiff;
            _ru.resolveNameConflictByRenaming_(dependency._did, socidRemote.soid(),
                    socidLocal.soid(), wasPresent, dependency._parent, pParent, vRemote,
                    dependency._meta, metaDiff, dependency._soidMsg, dependency._requested, cr, t);
            _ru.applyUpdateMetaAndContent_(sockidRemote, vRemote, cr, t);
            t.commit_();
        } catch (IOException e) {
            // Assert false as we want to know when exceptions happen in the DeadlockResolver
            assert false : Util.e(e);
        } catch (ExNotFound e) {
            // Assert false as we want to know when exceptions happen in the DeadlockResolver
            assert false : Util.e(e);
        } finally {
            t.end_();
        }
    }

}
