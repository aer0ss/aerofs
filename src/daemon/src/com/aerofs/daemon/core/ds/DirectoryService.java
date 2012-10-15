package com.aerofs.daemon.core.ds;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;


import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.LRUCache.IDataReader;
import com.aerofs.daemon.lib.db.DBCache;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.id.*;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

public class DirectoryService implements IDumpStatMisc
{
    private static final Logger l = Util.l(DirectoryService.class);

    private IMetaDatabase _mdb;
    private MapAlias2Target _alias2target;
    private IPhysicalStorage _ps;
    private IMapSIndex2SID _sidx2sid;
    private IMapSID2SIndex _sid2sidx;
    private IStores _ss;

    private DBCache<Path, SOID> _cacheDS;
    private DBCache<SOID, OA> _cacheOA;

    private final List<IDirectoryServiceListener> _listeners = Lists.newArrayList();

    /**
     * Interface for DirectoryService listeners
     *
     * All the methods are called during a transaction
     */
    public interface IDirectoryServiceListener
    {
        void objectCreated_(SOID obj, OID parent, Path pathTo, Trans t) throws SQLException;
        void objectDeleted_(SOID obj, OID parent, Path pathFrom, Trans t) throws SQLException;
        void objectMoved_(SOID obj, OID parentFrom, OID parentTo,
                Path pathFrom, Path pathTo, Trans t) throws SQLException;
        void objectContentModified_(SOID obj, Path path, boolean firstBranchAdded, Trans t)
                throws SQLException;
        void objectExpelled_(SOID obj, Trans t) throws SQLException;
        void objectAdmitted_(SOID obj, Trans t) throws SQLException;
        void objectSyncStatusChanged_(SOID obj, BitVector oldStatus, BitVector newStatus, Trans t)
                throws SQLException;
    }

    public void addListener_(IDirectoryServiceListener listener)
    {
        _listeners.add(listener);
    }

    @Inject
    public void inject_(IPhysicalStorage ps, IMetaDatabase mdb, MapAlias2Target alias2target,
            IStores ss, TransManager tm, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx)
    {
        _ps = ps;
        _mdb = mdb;
        _alias2target = alias2target;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _ss = ss;

        _cacheDS = new DBCache<Path, SOID>(tm, true, DaemonParam.DB.DS_CACHE_SIZE);
        _cacheOA = new DBCache<SOID, OA>(tm, DaemonParam.DB.OA_CACHE_SIZE);
    }

    public Set<OID> getChildren_(SOID soid)
        throws SQLException, ExNotDir, ExNotFound
    {
        if (!getOAThrows_(soid).isDir()) throw new ExNotDir();
        return _mdb.getChildren_(soid);
    }

    public OID getChild_(SIndex sidx, OID parent, String name) throws SQLException
    {
        return _mdb.getChild_(sidx, parent, name);
    }

    private final IDataReader<Path, SOID> _readerDS =
        new IDataReader<Path, SOID>() {
            @Override
            public SOID read_(Path path) throws SQLException
            {
                String[] elems = path.elements();
                SIndex sidx = _ss.getRoot_();
                OID oid = OID.ROOT;
                int i = 0;
                while (i < elems.length) {
                    OID child = getChild_(sidx, oid, elems[i]);
                    if (child == null) {
                        OA oa = getOA_(new SOID(sidx, oid));
                        if (!oa.isAnchor()) {
                            return null;
                        } else {
                            SOID soid = followAnchorNullable_(oa);
                            if (soid == null) return null;
                            sidx = soid.sidx();
                            oid = soid.oid();
                        }
                    } else {
                        oid = child;
                        i++;
                    }
                }

                return new SOID(sidx, oid);
            }
        };

    /**
     * @return null if the store doesn't exist, which should happen if and only if the anchor
     * is expelled.
     */
    @Nullable public SOID followAnchorNullable_(OA oa)
    {
        assert oa.isAnchor();
        SIndex sidx = _sid2sidx.getNullable_(SID.anchorOID2storeSID(oa.soid().oid()));
        assert oa.isExpelled() == (sidx == null);
        return sidx == null ? null : new SOID(sidx, OID.ROOT);
    }

    /**
     * @throws ExExpelled if the anchor is expelled, i.e. followAnchor_() returns null
     */
    @Nonnull public SOID followAnchorThrows_(OA oa) throws ExExpelled
    {
        SOID soid = followAnchorNullable_(oa);
        if (soid == null) throw new ExExpelled();
        return soid;
    }

    /**
     * @return null if not found
     */
    public @Nullable SOID resolveNullable_(Path path) throws SQLException
    {
        return _cacheDS.get_(path, _readerDS);
    }

    public SOID resolveThrows_(Path path)
        throws SQLException, ExNotFound
    {
        SOID soid = resolveNullable_(path);
        if (soid == null) throw new ExNotFound(path.toString());
        return soid;
    }

    public Path resolveThrows_(SOID soid) throws SQLException, ExNotFound
    {
        Path ret = resolveNullable_(soid);
        if (ret == null) throw new ExNotFound();
        return ret;
    }

    public @Nonnull Path resolve_(SOID soid) throws SQLException
    {
        Path ret = resolveNullable_(soid);
        assert ret != null : soid;
        return ret;
    }

    /**
     * N.B. an anchor has the same path as the root folder of its anchored store
     * @return null if not found
     */
    public @Nullable Path resolveNullable_(SOID soid) throws SQLException
    {
        OA oa = getOANullable_(soid);
        return oa == null ? null : resolve_(oa);
    }

    /**
     * N.B. an anchor has the same path as the root folder of its anchored store
     * @return unlike other versions of resolve(), it never returns null
     */
    public @Nonnull Path resolve_(@Nonnull OA oa) throws SQLException
    {
        List<String> elems = Lists.newArrayListWithCapacity(16);
        while (true) {
            if (oa.soid().oid().isRoot()) {
                if (oa.soid().sidx().equals(_ss.getRoot_())) {
                    break;
                } else {
                    // parent oid of the root encodes the parent store's sid
                    SOID soidAnchor = getAnchor_(oa.soid().sidx());
                    assert !soidAnchor.equals(oa.soid()) : soidAnchor + " " + oa;
                    oa = getOA_(soidAnchor);
                }
            }

            elems.add(oa.name());

            assert !oa.parent().equals(oa.soid().oid()) : oa;
            oa = getOA_(new SOID(oa.soid().sidx(), oa.parent()));
        }

        String[] path = new String[elems.size()];
        for (int i = 0; i < path.length; i++) {
            path[i] = elems.get(path.length - i - 1);
        }
        return new Path(path);
    }

    /**
     * @return the SOID of the given store's anchor
     * @pre {@code sidx} must not refer to the root store
     */
    private SOID getAnchor_(SIndex sidx) throws SQLException
    {
        assert !sidx.equals(_ss.getRoot_());
        SIndex sidxAnchor = _ss.getParent_(sidx);
        OID oidAnchor = SID.storeSID2anchorOID(_sidx2sid.get_(sidx));
        return new SOID(sidxAnchor, oidAnchor);
    }

    public OA getOAThrows_(SOID soid)
        throws ExNotFound, SQLException
    {
        OA oa = getOANullable_(soid);
        if (oa == null) throw new ExNotFound();
        return oa;
    }

    /**
     * @return whether there exists an OA for soid; the object is assumed to be a target
     */
    public boolean hasOA_(SOID soid) throws SQLException
    {
        return getOANullable_(soid) != null;
    }

    /**
     * @return whether there exists an OA for soid; the object can be an alias or a target
     */
    public boolean hasAliasedOA_(SOID soid) throws SQLException
    {
        return getAliasedOANullable_(soid) != null;
    }

    private final IDataReader<SOID, OA> _readerOA =
        new IDataReader<SOID, OA>() {
            @Override
            public OA read_(SOID soid) throws SQLException
            {
                OA oa = _mdb.getOA_(soid);
                if (oa == null) return null;

                Path path = resolve_(oa);
                if (oa.isFile()) {
                    for (Entry<KIndex, CA> en : oa.cas().entrySet()) {
                        en.getValue().setPhysicalFile_(_ps.newFile_(new SOKID(soid,
                                en.getKey()), path));
                    }
                } else {
                    oa.setPhyFolder_(_ps.newFolder_(soid, path));
                }

                return oa;
            }
        };

    /**
     * @return null if not found.
     */
    public @Nullable OA getOANullable_(SOID soid) throws SQLException
    {
        return _cacheOA.get_(soid, _readerOA);
    }

    public @Nonnull OA getOA_(SOID soid) throws SQLException
    {
        OA oa = getOANullable_(soid);
        assert oa != null : soid;
        return oa;
    }

    /**
     * @return returns object attribute including aliased ones.
     */
    public @Nullable OA getAliasedOANullable_(SOID soid) throws SQLException
    {
        OA oa = getOANullable_(soid);
        if (oa != null) return oa;
        OID target = _alias2target.getNullable_(soid);
        return target == null ? null : getOANullable_(new SOID(soid.sidx(), target));
    }

    /**
     * N.B. should be called by HdCreateObject only
     * @throws ExNotFound if the parent is not found
     */
    public void createOA_(OA.Type type, SIndex sidx, OID oid, OID oidParent, String name, int flags,
            Trans t) throws ExAlreadyExist, ExNotFound, SQLException
    {
        SOID soid = new SOID(sidx, oid);
        if (l.isInfoEnabled()) l.info(soid + ": create " + oidParent + "/" + name);

        assert !oid.equals(oidParent) : "s " + sidx + " o " + oid + " p " + oidParent;

        OA oaParent = getOAThrows_(new SOID(sidx, oidParent));

        assert oaParent.isDir();
        assert Normalizer.isNormalized(name, Form.NFC)
                : Joiner.on(' ').join(sidx, oid, Form.valueOf(name));

        _mdb.createOA_(sidx, oid, oidParent, name, type, flags, t);

        _cacheOA.invalidate_(soid);

        // all the children under the path need to be invalidated.
        _cacheDS.invalidateAll_();

        if (!oidParent.equals(OID.TRASH)) {
            Path path = resolve_(oaParent).append(name);
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectCreated_(soid, oaParent.soid().oid(), path, t);
            }
        }
    }

    public void createCA_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        OA oa = getOA_(soid);
        boolean first = oa.cas().isEmpty() && kidx == KIndex.MASTER;

        _mdb.createCA_(soid, kidx, t);
        _cacheOA.invalidate_(soid);

        // OA was invalidated so resolve from SOID
        Path path = resolve_(soid);
        for (IDirectoryServiceListener listener : _listeners) {
            listener.objectContentModified_(soid, path, first, t);
        }
    }

    public void deleteCA_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        _mdb.deleteCA_(soid, kidx, t);
        _cacheOA.invalidate_(soid);
    }

    /**
     * @return true if the object is under a trash folder
     *
     * This method is final because it would be a pain in the ass to mock
     */
    final public boolean isDeleted(@Nonnull OA oa) throws SQLException
    {
        SIndex sidx = oa.soid().sidx();
        while (!oa.parent().isRoot() && !oa.parent().isTrash()) {
            oa = getOA_(new SOID(sidx, oa.parent()));
        }
        return oa.parent().isTrash();
    }

    /**
     * N.B. should be called by ObjectMovement only
     */
    public void setOAParentAndName_(@Nonnull OA oa, @Nonnull OA oaParent, String name, Trans t)
        throws SQLException, ExAlreadyExist, ExNotDir
    {
        if (l.isInfoEnabled()) l.info(oa.soid() + ": move to " + oaParent.soid() + "/" + name);

        // assigning the child to a parent in a different store is always wrong.
        assert oa.soid().sidx().equals(oaParent.soid().sidx()) : oa + " " + oaParent;

        // assigning the child itself as the parent is always wrong (apart from anchors, which don't
        // call this)
        assert !oa.soid().equals(oaParent.soid()) : oa + " " + oaParent;

        if (!oaParent.isDir()) throw new ExNotDir();

        // verify the encoding of "name" is NFC
        assert Normalizer.isNormalized(name, Form.NFC)
                : Joiner.on(' ').join(oa, oaParent, Form.valueOf(name));

        Path pathFrom = resolve_(oa);
        Path pathTo = resolve_(oaParent).append(name);

        _mdb.setOAParentAndName_(oa.soid().sidx(), oa.soid().oid(), oaParent.soid().oid(), name, t);

        if (oa.isFile()) {
            // invalidate the old and new path
            _cacheDS.invalidate_(pathFrom);
            _cacheDS.invalidate_(pathTo);
            _cacheOA.invalidate_(oa.soid());
        } else {
            // Children objects and stores under the old and new paths need to be invalidated
            _cacheDS.invalidateAll_();
            // Physical objects of all the children objects needs to be invalidated
            _cacheOA.invalidateAll_();
        }

        // update activity log

        // Deleting a file means moving it to the trash but we don't currently flatten the object
        // hierarchy when doing so wich results in interesting race conditions :
        //
        // Assume devices A and B share the following hierarchy
        // foo
        // \---> bar
        // \---> baz
        //
        // Now if device A deletes foo and device B deletes bar concurrently, device A will attempt
        // to move bar from its parent foo into the trash but foo is itself already into the trash
        // which would cause multiple duplicate calls to the deletion callback (and a world of hurt)
        // if we simply checked whether the source parent is the trash. Instead we have to check
        // (recursively) whether the source is already under a trash folder.
        //
        // TODO: examine whether there is a valid reason to preserve tree structure in the trash
        // It is simpler for sync status (and activity log and probably any code building upon
        // deletion listeners) to assume that objects under the trash have a completely flat
        // hierarchy and it does not seem like it would adversely impact the syncing algorithm. It
        // might however be a problem for expulsion and re-admission.
        boolean fromTrash = isDeleted(oa);
        boolean toTrash = oaParent.soid().oid().isTrash() || isDeleted(oaParent);

        if (fromTrash && !toTrash) {
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectCreated_(oa.soid(), oaParent.soid().oid(), pathTo, t);
            }
        } else if (!fromTrash && toTrash) {
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectDeleted_(oa.soid(), oa.parent(),
                        pathFrom, t);
            }
        } else if (!fromTrash && !toTrash) {
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectMoved_(oa.soid(), oa.parent(), oaParent.soid().oid(),
                        pathFrom, pathTo, t);
            }
        } else {
            //noinspection ConstantConditions
            assert fromTrash && toTrash;
        }
    }

    public void setOAFlags_(SOID soid, int flags, Trans t)
        throws SQLException
    {
        OA oa = getOA_(soid);
        boolean oldExpelled = oa.isExpelled();

        _mdb.setOAFlags_(soid, flags, t);
        oa.flags(flags);

        boolean newExpelled = oa.isExpelled();

        if (oldExpelled != newExpelled) {
            if (newExpelled) {
                for (IDirectoryServiceListener listener : _listeners) {
                    listener.objectExpelled_(soid, t);
                }
            } else {
                for (IDirectoryServiceListener listener : _listeners) {
                    listener.objectAdmitted_(soid, t);
                }
            }
        }
    }

    /**
     * The callback interface for walk_()
     */
    public static interface IObjectWalker<T>
    {
        /**
         * This method is called on each object that is traversed. If the object is a directory or
         * anchor. This method is called _before_ traversing the children. If the object is a file,
         * the method is called immediately before postfixWalk_().
         *
         * @param oa the OA of the object currently being traversed. It may not reflect the current
         * state of the object attributes if walking on siblings updates the attributes.
         * @param cookieFromParent the value returned from the {@code prefixWalk_()} on the parent
         * of the current object.
         * @return the value that will be passed to the child objects in parameter {@code
         * cookieFromParent}. Set to null to avoid traversing the children if the current node is a
         * directory or anchor.
         */
        @Nullable T prefixWalk_(T cookieFromParent, OA oa)
                throws IOException, SQLException, ExStreamInvalid, ExNotFound, ExNotDir,
                ExAlreadyExist;

        /**
         * This method is called on each object that is traversed. If the object is a directory or
         * anchor. This method is called _after_ traversing the children. If the object is a file,
         * the method is called immediately after prefixWalk_().
         *
         * @param cookieFromParent the value returned from the {@code prefixWalk_()} on the parent
         * of the current object.
         * @param oa the OA of the object currently being traversed. It may not reflect the current
         * state of object attributes if prefixWalk() or walking on siblings or children updates the
         * attributes.
         */
        void postfixWalk_(T cookieFromParent, OA oa)
                throws IOException, ExAlreadyExist, SQLException, ExNotDir, ExNotFound,
                ExStreamInvalid;

    }
    /**
     * The class doesn't adapt the prefixWalk method because it doesn't know what to return.
     */
    public static abstract class ObjectWalkerAdapter<T> implements IObjectWalker<T>
    {
        @Override
        public void postfixWalk_(T cookeFromParent, OA oa) { }
    }

    /**
     * Traverse in DFS the directory tree rooted at {@code soid}.
     */
    public <T> void walk_(SOID soid, @Nullable T cookieFromParent, IObjectWalker<T> w)
            throws ExNotFound, SQLException, IOException, ExNotDir, ExStreamInvalid, ExAlreadyExist
    {
        OA oa = getOAThrows_(soid);

        T ret = w.prefixWalk_(cookieFromParent, oa);

        switch (oa.type()) {
        case DIR:
            if (ret != null) {
                for (OID oid : getChildren_(soid)) {
                    walk_(new SOID(soid.sidx(), oid), ret, w);
                }
            }
            break;
        case FILE:
            break;
        case ANCHOR:
            if (ret == null) break;
            soid = followAnchorNullable_(oa);
            if (soid == null) break;
            walk_(soid, ret, w);
            break;
        default:
            assert false;
        }

        w.postfixWalk_(cookieFromParent, oa);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "cDS");
        _cacheDS.dumpStatMisc(indent + indentUnit, indentUnit, ps);
        ps.println(indent + "cOA");
        _cacheOA.dumpStatMisc(indent + indentUnit, indentUnit, ps);
    }

    public void setFID_(SOID soid, @Nullable FID fid, Trans t) throws SQLException
    {
        // Roots do not have a valid FID
        assert !soid.oid().isRoot();
        _mdb.setFID_(soid, fid, t);
        _cacheOA.invalidate_(soid);
    }

    /**
     * @pre the CA must already exists
     */
    public void setCA_(SOKID sokid, long len, long mtime, @Nullable ContentHash h, Trans t)
        throws SQLException
    {
        OA oa = getOA_(sokid.soid());
        assert oa.isFile();

        // Non-master branches must have non-null hashes. See Hasher for detail.
        assert h != null || sokid.kidx().equals(KIndex.MASTER);

        _mdb.setCA_(sokid.soid(), sokid.kidx(), len, mtime, h, t);

        // update cached values
        oa.ca(sokid.kidx()).length(len);
        oa.ca(sokid.kidx()).mtime(mtime);

        Path path = resolve_(oa);
        for (IDirectoryServiceListener listener : _listeners) {
            listener.objectContentModified_(sokid.soid(), path, false, t);
        }
    }

    /**
     * Because fetching hashes from the db is expensive, we don't make the hash part of the CA
     * class. Instead, we fetch hashes only when needed.
     */
    public ContentHash getCAHash_(SOKID sokid) throws SQLException, ExNotFound
    {
        CA ca = getOAThrows_(sokid.soid()).caThrows(sokid.kidx());

        ContentHash ret = ca.physicalFile().getHash_();
        if (ret == null) ret = _mdb.getCAHash_(sokid.soid(), sokid.kidx());

        // Hashes of non-master branches should never be null.
        assert sokid.kidx().equals(KIndex.MASTER) || ret != null : sokid;

        return ret;
    }

    public void setCAHash_(SOKID sokid, ContentHash h, Trans t) throws SQLException
    {
        assert h != null;

        // The implementation asserts that the CA row corresponding to the sokid exists in the db.
        _mdb.setCAHash_(sokid.soid(), sokid.kidx(), h, t);
    }

    /**
     * @return null if not found
     */
    public @Nullable SOID getSOIDNullable_(FID fid) throws SQLException
    {
        return _mdb.getSOID_(fid);
    }

    /**
     * Invariant: if the object is expelled from the local device, the method must
     * return false on non-meta branches; otherwise, the object is admitted and
     * therefore can be present or absent.
     */
    public boolean isPresent_(SOCKID k) throws SQLException
    {
        OA oa = getOANullable_(k.soid());

        if (oa == null) {
            return false;
        } else if (k.cid().isMeta()) {
            return true;
        } else {
            if (oa.isExpelled()) {
                assert oa.caNullable(k.kidx()) == null;
                return false;
            } else {
                return oa.caNullable(k.kidx()) != null;
            }
        }
    }

    /**
     * Deleting meta-data entry is currently only required while performing aliasing.
     */
    public void deleteOA_(SOID soid, Trans t) throws SQLException
    {
        _mdb.deleteOA_(soid.sidx(), soid.oid(), t);
        _cacheDS.invalidateAll_();
        _cacheOA.invalidateAll_();
    }

    public String generateNameConflictFileName_(@Nonnull Path pParent, String name)
            throws SQLException, ExNotFound
    {
        do {
            name = Util.newNextFileName(name);
        } while (resolveNullable_(pParent.append(name)) != null);
        return name;
    }

    /**
     * Retrieve the raw sync status for an object
     * @return bitvector representing the sync status for all peers known to have {@code soid}
     *
     * NB: the returned sync status does not take into account the presence of content for recently
     * admitted files. Use this method with the utmost care...
     */
    public BitVector getRawSyncStatus_(SOID soid) throws SQLException
    {
        return _mdb.getSyncStatus_(soid);
    }

    /**
     * Retrieve the sync status for an object
     * @return bitvector representing the sync status for all peers known to have {@code soid}
     *
     * NB: the returned sync status is garanteed to be out-of-sync for recently admitted files whose
     * content has not been synced yet
     */
    public BitVector getSyncStatus_(SOID soid) throws SQLException
    {
        return adjustedSyncStatus_(soid, _mdb.getSyncStatus_(soid));
    }

    private BitVector adjustedSyncStatus_(SOID soid, BitVector status) throws SQLException
    {
        // Recently admitted files whose content has not been resynced yet are considered out of
        // sync even though the DB may still have old sync status information (which is required
        // to handle some exclusion/readmission corner cases in AggregateSyncStatus).
        OA oa = getOA_(soid);
        boolean recentlyAdmittedNotSynced = (!oa.isExpelled() && oa.isFile()
                                                     && oa.caMasterNullable() == null);
        return recentlyAdmittedNotSynced ? new BitVector() : status;
    }

    /**
     * Set the sync status for an object
     * NOTE: only SyncStatusSynchronizer should use that method
     * @param status bitvector representation of the sync status
     * @param t transaction (this method can only be called as part of a transaction)
     */
    public void setSyncStatus_(SOID soid, BitVector status, Trans t) throws SQLException
    {
        BitVector oldStatus = getSyncStatus_(soid);
        _mdb.setSyncStatus_(soid, status, t);
        status = adjustedSyncStatus_(soid, status);
        if (!oldStatus.equals(status)) {
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectSyncStatusChanged_(soid, oldStatus, status, t);
            }
        }
    }

    /**
     * Do not access aggregate sync status directly, always go through:
     * {@link com.aerofs.daemon.core.syncstatus.AggregateSyncStatus}
     */
    public CounterVector getAggregateSyncStatus_(SOID soid) throws SQLException
    {
        return _mdb.getAggregateSyncStatus_(soid);
    }

    /**
     * Do not modify aggregate sync status directly, it is automatically maintained by:
     * see {@link com.aerofs.daemon.core.syncstatus.AggregateSyncStatus}
     */
    public void setAggregateSyncStatus_(SOID soid, CounterVector agstat, Trans t)
            throws SQLException
    {
        _mdb.setAggregateSyncStatus_(soid, agstat, t);
    }
}
