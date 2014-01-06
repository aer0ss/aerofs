/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ds;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.LRUCache.IDataReader;
import com.aerofs.daemon.lib.db.DBCache;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.*;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;

import static com.google.common.base.Preconditions.checkState;

public class DirectoryServiceImpl extends DirectoryService implements ObjectSurgeon
{
    private static final Logger l = Loggers.getLogger(DirectoryService.class);

    private CfgStorageType _storageType;
    private IMetaDatabase _mdb;
    private MapAlias2Target _alias2target;
    private IMapSID2SIndex _sid2sidx;
    private FrequentDefectSender _fds;
    private AbstractPathResolver _pathResolver;

    private DBCache<Path, SOID> _cacheDS;
    private DBCache<SOID, OA> _cacheOA;

    private final List<IDirectoryServiceListener> _listeners = Lists.newArrayList();

    TransLocal<PreCommitFIDConsistencyVerifier> _fidConsistencyVerifier
            = new TransLocal<PreCommitFIDConsistencyVerifier>()
    {
        @Override
        protected PreCommitFIDConsistencyVerifier initialValue(Trans t)
        {
            assert _fds != null;
            PreCommitFIDConsistencyVerifier verifier
                    = new PreCommitFIDConsistencyVerifier(DirectoryServiceImpl.this, _fds);
            // TODO (MJ) this is a hack to support non-linked storage, which doesn't want to
            // verify consistency of FIDs. For non-linked storage, memory is wasted and cpu cycles
            // burned unnecessarily, but at least the consistency will never be verified.
            // TODO(HB) Ideally either:
            //  * all FID-related logic should be extruded from DS and moved to LinkedStorage
            //  * the concept of FID should be refactored to encompass non-linked storage
            // On a related note, FID consistency verification is not scalable when transactions
            // involve large number of objects and its very existence is indicative of deeper
            // design issues.
            if (_storageType.get() == StorageType.LINKED) t.addListener_(verifier);
            return verifier;
        }
    };

    @Override
    public void addListener_(IDirectoryServiceListener listener)
    {
        _listeners.add(listener);
    }

    @Inject
    public void inject_(IMetaDatabase mdb, MapAlias2Target alias2target,
            TransManager tm, IMapSID2SIndex sid2sidx, FrequentDefectSender fds,
            StoreDeletionOperators storeDeletionOperators, AbstractPathResolver pathResolver,
            CfgStorageType storageType)
    {
        _storageType = storageType;
        _mdb = mdb;
        _alias2target = alias2target;
        _sid2sidx = sid2sidx;
        _fds = fds;
        _pathResolver = pathResolver;

        _cacheDS = new DBCache<Path, SOID>(tm, true, DaemonParam.DB.DS_CACHE_SIZE);
        _cacheOA = new DBCache<SOID, OA>(tm, DaemonParam.DB.OA_CACHE_SIZE);

        storeDeletionOperators.add_(this);
    }

    @Override
    public Set<OID> getChildren_(SOID soid)
        throws SQLException, ExNotDir, ExNotFound
    {
        if (!getOAThrows_(soid).isDir()) throw new ExNotDir();
        return _mdb.getChildren_(soid);
    }

    @Override
    public OID getChild_(SIndex sidx, OID parent, String name) throws SQLException
    {
        OID child = _mdb.getChild_(sidx, parent, name);
        assert !parent.equals(child) : child;
        return child;
    }

    private final IDataReader<Path, SOID> _readerDS =
        new IDataReader<Path, SOID>() {
            @Override
            public SOID read_(Path path) throws SQLException
            {
                return _pathResolver.resolve_(path);
            }
        };

    /**
     * @return null if the store doesn't exist, which should happen if and only if the anchor
     * is expelled.
     */
    @Override
    @Nullable public SOID followAnchorNullable_(OA oa)
    {
        assert oa.isAnchor();
        SIndex sidx = _sid2sidx.getNullable_(SID.anchorOID2storeSID(oa.soid().oid()));
        assert oa.isExpelled() == (sidx == null);
        return sidx == null ? null : new SOID(sidx, OID.ROOT);
    }

    /**
     * @return null if not found
     */
    @Override
    @Nullable public SOID resolveNullable_(Path path) throws SQLException
    {
        return _cacheDS.get_(path, _readerDS);
    }

    /**
     * N.B. an anchor has the same path as the root folder of its anchored store
     * @return unlike other versions of resolve(), it never returns null
     */
    @Override
    @Nonnull public ResolvedPath resolve_(@Nonnull OA oa) throws SQLException
    {
        return _pathResolver.resolve_(oa);
    }

    private final IDataReader<SOID, OA> _readerOA =
        new IDataReader<SOID, OA>() {
            @Override @Nullable public OA read_(final SOID soid) throws SQLException
            {
                return _mdb.getOA_(soid);
            }
        };

    /**
     * @return null if not found.
     */
    @Override
    @Nullable public OA getOANullable_(SOID soid) throws SQLException
    {
        return _cacheOA.get_(soid, _readerOA);
    }

    /**
     * TODO (MJ) OA's are stale after performing any directory service write for oa.soid(), but
     * there is no safety check to warn devs of this fact.
     */
    @Override
    @Nonnull public OA getOA_(SOID soid) throws SQLException
    {
        OA oa = getOANullable_(soid);
        assert oa != null : soid + " " + _alias2target.getNullable_(soid);
        return oa;
    }

    @Override
    public int getSyncableChildCount_(SOID soid) throws SQLException
    {
        return _mdb.getSyncableChildCount_(soid);
    }

    /**
     * @return object attribute including aliased ones.
     */
    @Override
    @Nullable public OA getAliasedOANullable_(SOID soid) throws SQLException
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
    @Override
    public void createOA_(OA.Type type, SIndex sidx, OID oid, OID oidParent, String name, int flags,
            Trans t) throws ExAlreadyExist, ExNotFound, SQLException
    {
        assert !oid.equals(oidParent) : "s " + sidx + " o " + oid + " p " + oidParent;

        final SOID soid = new SOID(sidx, oid);
        final SOID soidParent = new SOID(sidx, oidParent);

        if (l.isDebugEnabled()) l.debug(soid + ": create " + oidParent + "/" + name);

        OA oaParent = getOAThrows_(soidParent);

        assert oaParent.isDir();

        _mdb.insertOA_(sidx, oid, oidParent, name, type, flags, t);

        _cacheOA.invalidate_(soid);

        // NB: we don't need to invalidate children because, duh, there can be no children...
        // To be more precise, the cache can only hold negative results (i.e null) for any path
        // under that of the object being created and it's totally fine because as said above, this
        // object does not have children yet and any future creation/move will invalidate the cache
        // as needed
        _cacheDS.invalidate_(resolve_(oaParent).append(name));

        if (!isTrashOrDeleted_(soidParent)) {
            Path path = resolve_(oaParent).append(name);
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectCreated_(soid, oaParent.soid().oid(), path, t);
            }
        }
    }

    @Override
    public void createCA_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        _mdb.insertCA_(soid, kidx, t);
        // TODO: update OA in place if present...
        _cacheOA.invalidate_(soid);

        Path path = resolve_(soid);
        for (IDirectoryServiceListener listener : _listeners) {
            listener.objectContentCreated_(new SOKID(soid, kidx), path, t);
        }

        _fidConsistencyVerifier.get(t).verifyAtCommitTime(soid);
    }

    @Override
    public void deleteCA_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        _mdb.deleteCA_(soid, kidx, t);
        // TODO: update OA in place if present...
        _cacheOA.invalidate_(soid);

        Path path = resolve_(soid);
        for (IDirectoryServiceListener listener : _listeners) {
            listener.objectContentDeleted_(new SOKID(soid, kidx), path, t);
        }

        _fidConsistencyVerifier.get(t).verifyAtCommitTime(soid);
    }

    /**
     * Replace an aliased OID with its target
     *
     * This method will:
     * 1. update the parent of all children of the {@code alias} to {@code target}
     * 2. replace the oid of the {@code alias} entry in the OA table with {@code target}
     * 3. update the oid of all CAs of the {@code alias} to {@code target}
     *
     * @pre there is no entry for {@code target} in the OA table
     *
     * USE WITH EXTREME CAUTION (Aliasing is the only expected caller)
     */
    @Override
    public void replaceOID_(SOID alias, SOID target, Trans t) throws SQLException, ExAlreadyExist
    {
        assert alias.sidx().equals(target.sidx()) : alias + " " + target;

        l.info("replace " + alias + " " + target);

        OA oa = getOA_(alias);
        Path path = resolve_(oa);

        if (oa.isDir()) {
            Set<OID> children = _mdb.getChildren_(alias);

            if (!children.isEmpty()) {
                _mdb.replaceParentInChildren_(alias.sidx(), alias.oid(), target.oid(), t);

                // TODO: update children OA in place instead of invalidating?
                for (OID child : children) {
                    _cacheOA.invalidate_(new SOID(alias.sidx(), child));
                }
            }
        }

        _mdb.replaceOAOID_(alias.sidx(), alias.oid(), target.oid(), t);
        if (oa.isFile()) {
            _mdb.replaceCA_(alias.sidx(), alias.oid(), target.oid(), t);
        }

        _cacheOA.invalidate_(alias);
        _cacheOA.invalidate_(target);

        _cacheDS.invalidate_(path);

        // TODO: listeners?
    }

    @Override
    public void swapOIDsInSameStore_(SIndex sidx, OID oid1, OID oid2, Trans t)
            throws SQLException, ExNotFound, ExNotDir
    {
        if (l.isDebugEnabled()) l.debug("in " + sidx + " swap oids " + oid1 + " " + oid2);

        Path path1 = resolve_(new SOID(sidx, oid1));
        Path path2 = resolve_(new SOID(sidx, oid2));

        // This method is only intended for swapping oids of nested directories
        // oid2 is nested under oid1
        checkState(path2.isUnder(path1), Joiner.on(' ').join(sidx, oid1, oid2));

        final OA oa1 = getOA_(new SOID(sidx, oid1));
        final OA oa2 = getOA_(new SOID(sidx, oid2));

        // This method is only intended for swapping oids among directories.
        checkState(oa1.isDir() && oa2.isDir(), oa1 + " " + oa2);

        Set<OID> oid1Children = getChildren_(oa1.soid());
        Set<OID> oid2Children = getChildren_(oa2.soid());

        checkState(Sets.intersection(oid1Children, oid2Children).isEmpty(),
            Joiner.on(' ').join(oa1, oa2, oid1Children, oid2Children));

        try {
            for (OID oid : oid1Children) _mdb.setOAParent_(sidx, oid, oid2, t);
            for (OID oid : oid2Children) _mdb.setOAParent_(sidx, oid, oid1, t);

            // Swap the rows for oid1 and oid2 (must create a temporary value to enable the swap)
            OID oidTemp = new OID(UniqueID.generate());
            _mdb.replaceOAOID_(sidx, oid1, oidTemp, t);
            _mdb.replaceOAOID_(sidx, oid2, oid1, t);
            _mdb.replaceOAOID_(sidx, oidTemp, oid2, t);

        } catch (ExAlreadyExist e) {
            // We don't expect AlreadyExist exception to be thrown
            throw new RuntimeException(Joiner.on(' ').join(oid1, oid2), e);
        }

        _cacheOA.invalidateAll_();
        _cacheDS.invalidateAll_();
    }

    /**
     * N.B. should be called by ObjectMovement only
     */
    @Override
    public void setOAParentAndName_(@Nonnull OA oa, @Nonnull OA oaParent, String name, Trans t)
        throws SQLException, ExAlreadyExist, ExNotDir
    {
        if (l.isDebugEnabled()) l.debug(oa.soid() + ": move to " + oaParent.soid() + "/" + name);

        // assigning the child to a parent in a different store is always wrong.
        assert oa.soid().sidx().equals(oaParent.soid().sidx()) : oa + " " + oaParent;

        // assigning the child itself as the parent is always wrong (apart from anchors, which don't
        // call this)
        assert !oa.soid().equals(oaParent.soid()) : oa + " " + oaParent;

        if (!oaParent.isDir()) throw new ExNotDir();

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
        boolean fromTrash = isDeleted_(oa);
        boolean toTrash = oaParent.soid().oid().isTrash() || isDeleted_(oaParent);

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

    @Override
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

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "cDS");
        _cacheDS.dumpStatMisc(indent + indentUnit, indentUnit, ps);
        ps.println(indent + "cOA");
        _cacheOA.dumpStatMisc(indent + indentUnit, indentUnit, ps);
    }

    @Override
    public void unsetFID_(SOID soid, Trans t) throws SQLException
    {
        setFIDImpl_(soid, null, t);
    }

    @Override
    public void setFID_(SOID soid, @Nonnull FID fid, Trans t) throws SQLException
    {
        setFIDImpl_(soid, fid, t);
    }

    private void setFIDImpl_(final SOID soid, @Nullable final FID fid, Trans t) throws SQLException
    {
        // Roots do not have a valid FID
        assert !soid.oid().isRoot();
        _mdb.setFID_(soid, fid, t);
        // TODO: update OA if cached...
        _cacheOA.invalidate_(soid);

        _fidConsistencyVerifier.get(t).verifyAtCommitTime(soid);
    }

    /**
     * @pre the CA must already exists
     */
    @Override
    public void setCA_(SOKID sokid, long len, long mtime, @Nullable ContentHash h, Trans t)
        throws SQLException
    {
        OA oa = getOA_(sokid.soid());
        assert oa.isFile();

        // Non-master branches must have non-null hashes. See Hasher for detail.
        assert h != null || sokid.kidx().equals(KIndex.MASTER);
        // Mtime since the 1970 epoch must not be negative
        assert mtime >= 0 : Joiner.on(' ').join(sokid, oa, mtime);

        _mdb.setCA_(sokid.soid(), sokid.kidx(), len, mtime, h, t);

        // update cached values
        oa.ca(sokid.kidx()).length(len);
        oa.ca(sokid.kidx()).mtime(mtime);

        Path path = resolve_(oa);
        for (IDirectoryServiceListener listener : _listeners) {
            listener.objectContentModified_(sokid, path, t);
        }

        _fidConsistencyVerifier.get(t).verifyAtCommitTime(sokid.soid());
    }

    /**
     * Because fetching hashes from the db is expensive, we don't make the hash part of the CA
     * class. Instead, we fetch hashes only when needed.
     */
    @Override
    public ContentHash getCAHash_(SOKID sokid) throws SQLException, ExNotFound
    {
        OA oa = getOAThrows_(sokid.soid());
        oa.caThrows(sokid.kidx());
        ContentHash ret = _mdb.getCAHash_(sokid.soid(), sokid.kidx());

        // Hashes of non-master branches should never be null.
        assert sokid.kidx().equals(KIndex.MASTER) || ret != null : sokid;

        return ret;
    }

    @Override
    public void setCAHash_(SOKID sokid, @Nonnull ContentHash h, Trans t) throws SQLException
    {
        // The implementation asserts that the CA row corresponding to the sokid exists in the db.
        _mdb.setCAHash_(sokid.soid(), sokid.kidx(), h, t);
    }

    /**
     * @return null if not found
     */
    @Override
    @Nullable public SOID getSOIDNullable_(FID fid) throws SQLException
    {
        return _mdb.getSOID_(fid);
    }

    /**
     * Deleting meta-data entry is currently only required while performing aliasing.
     */
    @Override
    public void deleteOA_(SOID soid, Trans t) throws SQLException
    {
        l.info("delete " + soid);
        // need to preserve the OA for the listener callback
        OA oa = getOA_(soid);
        Path path = null;
        BitVector bv = null;
        if (!oa.isExpelled()) {
            // TODO: aliasing should cleanup CAs and children before deleting OAs so all this
            // listner gymnastic should be avoidable...
            path = resolve_(oa);
            bv = getSyncStatus_(soid);
        }

        _mdb.deleteOA_(soid.sidx(), soid.oid(), t);

        if (path != null) {
            _cacheDS.invalidate_(path);
        } else {
            // TODO: is this really needed?
            _cacheDS.invalidateAll_();
        }
        _cacheOA.invalidate_(soid);

        // must call after removing the OA so that syncable child count is accurate
        if (!oa.isExpelled()) {
            for (IDirectoryServiceListener listener : _listeners) {
                listener.objectObliterated_(oa, bv, path, t);
            }
        }
    }

    @Override
    public IDBIterator<SOKID> getAllNonMasterBranches_() throws SQLException
    {
        return _mdb.getAllNonMasterBranches_();
    }

    /**
     * Retrieve the raw sync status for an object
     * @return bitvector representing the sync status for all peers known to have {@code soid}
     *
     * NB: the returned sync status does not take into account the presence of content for recently
     * admitted files. Use this method with the utmost care...
     */
    @Override
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
    @Override
    public BitVector getSyncStatus_(SOID soid) throws SQLException
    {
        return adjustedSyncStatus_(soid, _mdb.getSyncStatus_(soid));
    }

    private BitVector adjustedSyncStatus_(SOID soid, BitVector status) throws SQLException
    {
        // Files without a master branch are considered out of sync regardless of the content
        // of the sync status column in the DB
        OA oa = getOA_(soid);
        boolean fileWithoutMasterBranch = (oa.isFile() && oa.caMasterNullable() == null);
        return fileWithoutMasterBranch ? new BitVector() : status;
    }

    /**
     * Set the sync status for an object
     * NOTE: only SyncStatusSynchronizer should use that method
     * @param status bitvector representation of the sync status
     * @param t transaction (this method can only be called as part of a transaction)
     */
    @Override
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
    @Override
    public CounterVector getAggregateSyncStatus_(SOID soid) throws SQLException
    {
        return _mdb.getAggregateSyncStatus_(soid);
    }

    /**
     * Do not modify aggregate sync status directly, it is automatically maintained by:
     * see {@link com.aerofs.daemon.core.syncstatus.AggregateSyncStatus}
     */
    @Override
    public void setAggregateSyncStatus_(SOID soid, CounterVector agstat, Trans t)
            throws SQLException
    {
        _mdb.setAggregateSyncStatus_(soid, agstat, t);
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        _mdb.deleteOAsAndCAsForStore_(sidx, t);
        _cacheDS.invalidateAll_();
        _cacheOA.invalidateAll_();
    }
}
