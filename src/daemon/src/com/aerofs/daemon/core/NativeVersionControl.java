package com.aerofs.daemon.core;

import java.sql.SQLException;

import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.lib.id.SOID;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.NativeTickRow;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO: caching

public class NativeVersionControl extends AbstractVersionControl<NativeTickRow>
{
    private static Logger l = Util.l(NativeVersionControl.class);

    private final INativeVersionDatabase _nvdb;
    private final ICollectorSequenceDatabase _csdb;
    private final MapAlias2Target _alias2target;
    private final ActivityLog _al;

    @Inject
    public NativeVersionControl(INativeVersionDatabase nvdb, ICollectorSequenceDatabase csdb,
            MapAlias2Target alias2target, CfgLocalDID cfgLocalDID, TransLocalVersionAssistant tlva,
            ActivityLog al, StoreDeletionOperators sdo)
    {
        super(nvdb, cfgLocalDID, tlva, sdo);
        _nvdb = nvdb;
        _csdb = csdb;
        _alias2target = alias2target;
        _al = al;
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
            deleteKMLVersion_(k.socid(), new Version().set_(myDid, myKMLTick), t);
        }

        Version v = new Version().set_(myDid, newTick);
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
            addKMLVersionAndCollectorSequenceNoAssert_(socid, new Version().set_(did, tick), t);
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

        _al.localVersionAdded_(k.soid(), v, t);

        if (l.isDebugEnabled()) l.debug("add local ver " + k + " " + v);
        _nvdb.addLocalVersion_(k, v, t);
        _tlva.get(t).localVersionAdded_(k.socid(), v);
    }

    public void deleteLocalVersion_(SOCKID k, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_())  return;

        if (l.isDebugEnabled()) l.debug("del local ver " + k + " " + v);
        _nvdb.deleteLocalVersion_(k, v, t);
        _tlva.get(t).versionDeleted_(k.socid(), v);
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
        _tlva.get(t).versionDeletedPermanently_(k.socid(), v);
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
        if (addKMLVersionImpl_(socid, v, expensiveAssert, t)) _csdb.addCS_(socid, t);
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
        _tlva.get(t).kmlVersionAdded_(socid, v);
        return true;
    }

    public void deleteKMLVersion_(SOCID socid, Version v, Trans t)
        throws SQLException
    {
        if (v.isZero_()) return;

        if (l.isDebugEnabled()) l.debug("del kml ver " + socid + " " + v);
        _nvdb.deleteKMLVersion_(socid, v, t);
        _tlva.get(t).versionDeleted_(socid, v);
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
        _tlva.get(t).versionDeletedPermanently_(socid, v);
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
        addKMLVersionAndCollectorSequenceNoAssert_(socid, new Version().set_(_cfgLocalDID.get(),
                tr._tick), t);
    }
}
