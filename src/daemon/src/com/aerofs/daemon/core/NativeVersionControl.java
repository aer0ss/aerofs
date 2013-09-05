package com.aerofs.daemon.core;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;

import com.aerofs.base.C;
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
        assert !myKMLTick.isAlias() && myKMLTick.getLong() < newTick.getLong();
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
    public @Nullable Tick getLocalTick_(SOCKID k) throws SQLException
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
        assert socid.cid().isMeta() || !tick.isAlias() : ("s " + socid + " d " + did + " t" + tick);

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
        return _nvdb.getLocalVersion_(k);
    }

    public void addLocalVersion_(SOCKID k, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_()) return;

        if (l.isDebugEnabled()) l.debug("add local ver " + k + " " + v);
        _nvdb.addLocalVersion_(k, v, t);
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

        if (l.isDebugEnabled()) l.debug("del local ver " + k + " " + v);
        _nvdb.deleteLocalVersion_(k, v, t);
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

        if (l.isDebugEnabled()) l.debug("del local ver perm " + k + " " + v);
        _nvdb.deleteLocalVersion_(k, v, t);
        _tlva.get(t).versionDeletedPermanently_(k.socid());
    }

    public void moveAllLocalVersions_(SOCID alias, SOCID target, Trans t) throws SQLException
    {
        _nvdb.moveAllLocalVersions_(alias, target, t);

        _tlva.get(t).localVersionAdded_(target);
        _tlva.get(t).versionDeletedPermanently_(alias);

        // TODO: can sidx of two objects be different?
        // TODO: listeners (syncstat and activity log)?
    }

    public @Nonnull Version getKMLVersion_(SOCID socid) throws SQLException
    {
        Version v = _nvdb.getKMLVersion_(socid);

        // IMPORTANT invariant: KML should not be shadowed by any local version.
        // Here we only test the invariant against the MASTER branch for simplicity
        assert v.shadowedBy_(getLocalVersion_(new SOCKID(socid))).isZero_() :
            socid + " " + v + " " + getLocalVersion_(new SOCKID(socid));

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

        if (l.isDebugEnabled()) l.debug("add kml ver " + socid + " " + v);

        if (expensiveAssert) {
            // assert the KML to be added is disjoint from all the versions of socid.
            // the call to getAllVersions is expensive.
            assert v.sub_(getAllVersions_(socid)).equals(v) :
                    socid + " " + v + " " + getAllVersions_(socid);
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

        if (l.isDebugEnabled()) l.debug("del kml ver " + socid + " " + v);
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

        if (l.isDebugEnabled()) l.debug("del kml ver perm " + socid + " " + v);
        _nvdb.deleteKMLVersion_(socid, v, t);
        _tlva.get(t).versionDeletedPermanently_(socid);
    }

    public Version getAllLocalVersions_(SOCID socid) throws SQLException
    {
        return _nvdb.getAllLocalVersions_(socid);
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
     * Helper class to compute version hash
     * Grouping meta and content ticks for a given DID before digest computation
     * reduces the amount of data to digest by 33% and does not add significant
     * precomputation overhead since the entries need to be sorted anyway...
     */
    private static class TickPair
    {
        public Tick _mt, _ct;

        public long metaTick()
        {
            return _mt != null ? _mt.getLong() : 0;
        }

        public long contentTick()
        {
            return _ct != null ? _ct.getLong() : 0;
        }

        public TickPair(Tick meta)
        {
            _mt = meta;
        }
    }

    /**
     * @return version hash of the given object
     */
    public byte[] getVersionHash_(SOID soid) throws SQLException
    {
        // aggregate MASTER versions for both meta and content components
        // we intentionally do NOT take conflict branches into account as:
        //   * the sync status could appear as out-of-sync between two users with the exact same
        //   MASTER branch which would go against user expectations
        //   * there is no easy way to take conflict branches that does not leave room for
        //   inconsistent results

        // Map needs to be sorted for deterministic version hash computation
        SortedMap<DID, TickPair> aggregated = Maps.newTreeMap();

        Version vm = getLocalVersion_(new SOCKID(soid, CID.META, KIndex.MASTER));
        for (Entry<DID, Tick> e : vm.getAll_().entrySet()) {
            aggregated.put(e.getKey(), new TickPair(e.getValue()));
        }

        Version vc = getLocalVersion_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER));
        for (Entry<DID, Tick> e : vc.getAll_().entrySet()) {
            TickPair tp = aggregated.get(e.getKey());
            if (tp == null) {
                tp = new TickPair(null);
                aggregated.put(e.getKey(), tp);
            }
            tp._ct = e.getValue();
        }

        // make a digest from that aggregate
        // (no security concern here, only compactness matters so MD5 is fine)
        MessageDigest md = SecUtil.newMessageDigestMD5();
        for (Entry<DID, TickPair> e : aggregated.entrySet()) {
            md.update(e.getKey().getBytes());
            md.update(toByteArray(e.getValue().metaTick()));
            md.update(toByteArray(e.getValue().contentTick()));
        }

        return md.digest();
    }

    private static byte[] toByteArray(long l)
    {
        return ByteBuffer.allocate(C.LONG_SIZE).putLong(l).array();
    }
}
