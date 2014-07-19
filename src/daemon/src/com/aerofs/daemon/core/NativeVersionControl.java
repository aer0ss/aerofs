package com.aerofs.daemon.core;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.store.MapSIndex2Contributors;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.NativeTickRow;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

// TODO: caching

public class NativeVersionControl extends AbstractVersionControl<NativeTickRow>
{
    private static Logger l = Loggers.getLogger(NativeVersionControl.class);

    private final INativeVersionDatabase _nvdb;
    private final ICollectorSequenceDatabase _csdb;
    private final MapAlias2Target _alias2target;
    private final MapSIndex2Contributors _sidx2contrib;

    public static interface IVersionControlListener
    {
        void localVersionAdded_(SOCKID sockid, Version v, Trans t) throws SQLException;
    }

    private final List<IVersionControlListener> _listeners = Lists.newArrayList();

    @Inject
    public NativeVersionControl(INativeVersionDatabase nvdb, ICollectorSequenceDatabase csdb,
            MapAlias2Target alias2target, CfgLocalDID cfgLocalDID, TransLocalVersionAssistant tlva,
            StoreDeletionOperators sdo, MapSIndex2Contributors sidx2contrib)
    {
        super(nvdb, cfgLocalDID, tlva, sdo);
        _nvdb = nvdb;
        _csdb = csdb;
        _alias2target = alias2target;
        _sidx2contrib = sidx2contrib;
    }

    public void addListener_(IVersionControlListener listener)
    {
        _listeners.add(listener);
    }

    /**
     * Do not call this method directly. Use VersionUpdater instead, which provides more high-level
     * functions than merely updating the version database.
     */
    public void updateMyVersion_(SOCKID k, boolean alias, Trans t)
            throws SQLException
    {
        Tick newTick =  alias ? _maxTick.incAlias() : _maxTick.incNonAlias();
        DID myDid = _cfgLocalDID.get();

        /* Delete the KML tick shadowed by the new update. It is an unusual situation but can happen
         * in the following sequence of events:
         *
         * 1. Peer A updates a file, generating <A1>
         * 2. B downloads a file and updates it again, generating <A1B1>
         * 3. The new version has updated to A, causing the KML on A to be <B1>
         * 4. B expels the file before A gets a chance to download the update
         * 5. B admits the file, and download the file from A. Thus, the KML on B is <B1> and local
         *    version is <A1>
         * 6. B updates the file again, causing KML <B1> shadowed by a new local version <A1B2>
         */

        Tick myKMLTick = getKMLVersion_(k.socid()).get_(myDid);
        checkState(!myKMLTick.isAlias() && myKMLTick.getLong() < newTick.getLong());
        if (!myKMLTick.equals(Tick.ZERO)) {
            deleteKMLVersion_(k.socid(), Version.of(myDid, myKMLTick), t);
        }

        Version v = Version.of(myDid, newTick);
        addLocalVersion_(k, v, t);

        _nvdb.setGreatestTick_(newTick, t);
        _maxTick = newTick;
    }

    /**
     * similar to getLocalVersion but only returns the tick of the local device
     * @return null iff the local DID does not have an entry in the version vector, i.e. we have
     *         made no local modifications
     */
    public @Nullable Tick getLocalTickNullable_(SOCKID k) throws SQLException
    {
        return _nvdb.getLocalTick_(k);
    }

    /**
     * @return whether the tick was unknown
     */
    public boolean tickReceived_(SOCID socid, DID did, Tick tick, Trans t)
        throws SQLException
    {
        // A non-meta socid should never have an alias tick.
        checkState(socid.cid().isMeta() || !tick.isAlias(), "s %s d %s t%s", socid, did, tick);

        // If the tick is for a non-aliased object, but the socid is aliased,
        // reassign the tick from the socid to its target. This ensures that
        // the versions of local aliased objects only propagate information about aliasing
        // not META or CONTENT components.
        // Safety proof:
        //   If the SOCID is aliased and we move the non-alias tick to its target's KML,
        //   we are assuming that the sender of this tick will eventually perform aliasing on the
        //   given socid and we will be able to download the content via the target OID.
        if (!tick.isAlias()) {
            final SOID dereferenced = _alias2target.dereferenceAliasedOID_(socid.soid());
            socid = new SOCID(dereferenced, socid.cid());
        }

        // Comment A
        // It is possible to receive a <did, tick> pair for the input SOCID (say o2) that
        // duplicates that pair for a different SOCID (o1) stored locally. This could happen when
        // o1 aliases to o2 (i.e. o1->o2) on the remote peer (but not yet locally), and the
        // <did, tick> pair in question was migrated from o1 to o2 on that remote peer.
        // Unfortunately this means that we must tolerate duplicate ticks in the system,
        // Still, the duplicates should disappear when aliasing information has propagated to all
        // devices involved in a name conflict. However, if any of those devices go offline
        // permanently, the duplicates will never be merged/removed
        if (!_nvdb.isTickKnown_(socid, did, tick)) {
            addKMLVersionAndCollectorSequenceNoAssert_(socid, Version.of(did, tick), t);
            return true;
        } else {
            return false;
        }
    }

    public @Nonnull Version getLocalVersion_(SOCKID k) throws SQLException
    {
        Version v = _nvdb.getLocalVersion_(k);
        // NB: local versions should always be homogeneous
        // however KMLs may very well include a mix of regular and alias ticks
        checkState(v.isHomogeneous_(), "%s %s", k, v);
        return v;
    }

    public void addLocalVersion_(SOCKID k, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_()) return;

        l.debug("add local ver {} {}", k, v);
        checkArgument(v.isHomogeneous_(), "%s %s", k, v);
        _nvdb.addLocalVersion_(k, v, t);
        // TODO: check homogeneity of result?
        _tlva.get(t).localVersionAdded_(k.socid());
        for (DID did : v.getAll_().keySet()) {
            _sidx2contrib.addContributor_(k.sidx(), did, t);
        }

        for (IVersionControlListener listener : _listeners) listener.localVersionAdded_(k, v, t);
    }

    public void deleteLocalVersion_(SOCKID k, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_())  return;

        l.debug("del local ver {} {}", k, v);
        checkArgument(v.isHomogeneous_(), "%s %s", k, v);
        _nvdb.deleteLocalVersion_(k, v, t);
        // TODO: check homogeneity of result?
        _tlva.get(t).versionDeleted_(k.socid());
    }

    /**
     * call this method when deleting a version permanently from the component
     * without adding back to the same component, e.g. during aliasing
     */
    public void deleteLocalVersionPermanently_(SOCKID k, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_()) return;

        l.debug("del local ver perm {} {}", k, v);
        checkArgument(v.isHomogeneous_(), "%s %s", k, v);
        _nvdb.deleteLocalVersion_(k, v, t);
        // TODO: check homogeneity of result?
        _tlva.get(t).versionDeletedPermanently_(k.socid());
    }

    public void moveAllLocalVersions_(SOCID alias, SOCID target, Trans t) throws SQLException
    {
        checkArgument(alias.sidx().equals(target.sidx()), "%s %s", alias, target);
        _nvdb.moveAllLocalVersions_(alias, target, t);

        _tlva.get(t).localVersionAdded_(target);
        _tlva.get(t).versionDeletedPermanently_(alias);

        // TODO: listeners?
    }

    /**
     * When expelling content, all local ticks should be moved to KMLs
     *
     * There's a dum way to do it which involves reading version vectors
     * branch by branch and accumulating them in memory, then deleting
     * ticks for local branchs and updating KMLs. All of this causes a
     * recomputation of max ticks.
     *
     * The smarter way to do it is to delete all local ticks and copy
     * max ticks into KMLs, which does not trigger a recomputation of
     * max ticks.
     */
    public void moveAllContentTicksToKML_(SOID soid, Trans t) throws SQLException
    {
        SOCID socid = new SOCID(soid, CID.CONTENT);
        // NB: listeners are not welcome here
        _nvdb.deleteAllVersions_(socid, t);
        _nvdb.moveMaxTicksToKML_(socid, t);
    }

    public @Nonnull Version getKMLVersion_(SOCID socid) throws SQLException
    {
        Version v = _nvdb.getKMLVersion_(socid);

        // IMPORTANT invariant: KML should not be shadowed by any local version.
        // Here we only test the invariant against the MASTER branch for simplicity
        Version l = getLocalVersion_(new SOCKID(socid));
        checkState(v.shadowedBy_(l).isZero_(), "%s %s %s", socid, v, l);

        return v;
    }

    public void addKMLVersionAndCollectorSequence_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        addKMLVersionAndCollectorSequenceImpl_(socid, v, true, t);
    }

    /**
     * Use this method only if performance is critical and it's apparent by looking at the caller's
     * code that the assert in addKMLVersionImpl_ will not fail.
     */
    public void addKMLVersionAndCollectorSequenceNoAssert_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        addKMLVersionAndCollectorSequenceImpl_(socid, v, false, t);
    }

    public boolean addKMLVersion_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        return addKMLVersionImpl_(socid, v, true, t);
    }

    /**
     * Use this method only if performance is critical and it's apparent by looking at the caller's
     * code that the assert in addKMLVersionImpl_ will not fail.
     */
    public boolean addKMLVersionNoAssert_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        return addKMLVersionImpl_(socid, v, false, t);
    }

    /**
     * In addition to adding the KML version, this method adds the component to the collector-
     * sequence table. Note that adding to the table doesn't automatically trigger collecting of
     * the object. It's triggered only when collector filters are received. So to start
     * collecting right away, the caller should do an immediate anti-entropy pull using {@link
     * Store#startAntiEntropy_()}.
     */
    private void addKMLVersionAndCollectorSequenceImpl_(SOCID socid, Version v,
            boolean expensiveAssert, Trans t) throws SQLException
    {
        if (addKMLVersionImpl_(socid, v, expensiveAssert, t)) _csdb.insertCS_(socid, t);
    }

    /**
     * @return true if v is non-zero
     */
    private boolean addKMLVersionImpl_(SOCID socid, Version v, boolean expensiveAssert, Trans t)
            throws SQLException
    {
        if (v.isZero_()) return false;

        l.debug("add kml ver {} {}", socid, v);

        if (expensiveAssert) {
            // assert the KML to be added is disjoint from all the versions of socid.
            // the call to getAllVersions is expensive.
            Version all = getAllVersions_(socid);
            checkState(v.sub_(all).equals(v), "%s %s %s", socid, v, all);
        }

        _nvdb.addKMLVersion_(socid, v, t);
        _tlva.get(t).kmlVersionAdded_(socid);
        for (DID did : v.getAll_().keySet()) {
            _sidx2contrib.addContributor_(socid.sidx(), did, t);
        }
        return true;
    }

    public void deleteKMLVersion_(SOCID socid, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_()) return;

        l.debug("del kml ver {} {}", socid, v);
        _nvdb.deleteKMLVersion_(socid, v, t);
        _tlva.get(t).versionDeleted_(socid);
    }

    /**
     * call this method when deleting a version permanently from the component
     * without adding back to the same component, e.g. during aliasing
     */
    public void deleteKMLVersionPermanently_(SOCID socid, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_())  return;

        l.debug("del kml ver perm {} {}", socid, v);
        _nvdb.deleteKMLVersion_(socid, v, t);
        _tlva.get(t).versionDeletedPermanently_(socid);
    }

    public Version getAllLocalVersions_(SOCID socid) throws SQLException
    {
        Version v = _nvdb.getAllLocalVersions_(socid);
        checkState(v.isHomogeneous_());
        return v;
    }

    /**
     * Note: this call may be expensive. Use it sparingly.
     */
    public @Nonnull Version getAllVersions_(SOCID socid) throws SQLException
    {
        return _nvdb.getAllVersions_(socid);
    }

    @Override
    protected void restoreTickRow_(SOCID socid, NativeTickRow tr, Trans t)
            throws SQLException
    {
        addKMLVersionAndCollectorSequenceNoAssert_(socid,
                Version.of(_cfgLocalDID.get(), tr._tick), t);
    }

    /**
     * @return version hash of the given object
     */
    public byte[] getVersionHash_(SOID soid, CID cid) throws SQLException
    {
        // Map needs to be sorted for deterministic version hash computation
        SortedMap<DID, Long> ticks = Maps.newTreeMap();

        Version vm = getLocalVersion_(new SOCKID(soid, cid, KIndex.MASTER));
        for (Entry<DID, Tick> e : vm.getAll_().entrySet()) {
            ticks.put(e.getKey(), e.getValue().getLong());
        }

        // make a digest from that aggregate
        // (no security concern here, only compactness matters so MD5 is fine)
        MessageDigest md = SecUtil.newMessageDigestMD5();
        for (Entry<DID, Long> e : ticks.entrySet()) {
            md.update(e.getKey().getBytes());
            md.update(BaseUtil.toByteArray(e.getValue()));
        }

        return md.digest();
    }
}
