package com.aerofs.daemon.core.ds;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Set;


import com.aerofs.daemon.core.ActivityLog;
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
import com.aerofs.lib.id.*;

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
    private ActivityLog _al;

    private DBCache<Path, SOID> _cacheDS;
    private DBCache<SOID, OA> _cacheOA;

    @Inject
    public void inject_(IPhysicalStorage ps, IMetaDatabase mdb, MapAlias2Target alias2target,
            IStores ss, TransManager tm, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx,
            ActivityLog al)
    {
        _ps = ps;
        _mdb = mdb;
        _alias2target = alias2target;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _ss = ss;
        _al = al;

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
                        OA oa = getOANullable_(new SOID(sidx, oid));
                        assert oa != null;
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
        assert ret != null;
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
    public Path resolve_(OA oa) throws SQLException
    {
        ArrayList<String> elems = new ArrayList<String>(16);
        while (true) {
            if (oa.soid().oid().isRoot()) {
                if (oa.soid().sidx().equals(_ss.getRoot_())) {
                    break;
                } else {
                    // parent oid of the root encodes the parent store's sid
                    SOID soidAnchor = getAnchor_(oa.soid().sidx());
                    oa = getOANullable_(soidAnchor);
                }
            }

            elems.add(oa.name());
            oa = getOANullable_(new SOID(oa.soid().sidx(), oa.parent()));
            assert oa != null;
        }

        String[] path = new String[elems.size()];
        for (int i = 0; i < path.length; i++) {
            path[i] = elems.get(path.length - i - 1);
        }
        return new Path(path);
    }

    /**
     * @param sidx must not refer to the root store
     * @return the SOID of the given store's anchor
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

        OA oaParent = getOAThrows_(new SOID(sidx, oidParent));

        assert oaParent.isDir();

        _mdb.createOA_(sidx, oid, oidParent, name, type, flags, t);

        _cacheOA.invalidate_(soid);

        // all the children under the path need to be invalidated.
        _cacheDS.invalidateAll_();

        // TODO consider using a listener once DirectoryService activity listeners are supported
        if (!oidParent.equals(OID.TRASH)) {
            _al.objectCreated_(soid, resolve_(oaParent).append(name), t);
        }
    }

    public void createCA_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        _mdb.createCA_(soid, kidx, t);
        _cacheOA.invalidate_(soid);

        _al.objectModified_(soid, resolve_(soid), t);
    }

    public void deleteCA_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        _mdb.deleteCA_(soid, kidx, t);
        _cacheOA.invalidate_(soid);
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
        // TODO consider using a listener once DirectoryService activity listeners are supported
        boolean fromTrash = oa.parent().equals(OID.TRASH);
        boolean toTrash = oaParent.soid().oid().equals(OID.TRASH);
        if (fromTrash && !toTrash) {
            _al.objectCreated_(oa.soid(), pathTo, t);
        } else if (!fromTrash && toTrash) {
            _al.objectDeleted_(oa.soid(), pathFrom, t);
        } else if (!fromTrash && !toTrash) {
            _al.objectMoved_(oa.soid(), pathFrom, pathTo, t);
        } else {
            assert fromTrash && toTrash;
        }
    }

    public void setOAFlags_(SOID soid, int flags, Trans t)
        throws SQLException
    {
        _mdb.setOAFlags_(soid, flags, t);

        OA oa = _cacheOA.get_(soid);
        if (oa != null) oa.flags(flags);
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
        T prefixWalk_(T cookieFromParent, OA oa) throws Exception;

        /**
         * This method is called on each object that is traversed. If the object is a directory or
         * anchor. This method is called _after_ traversing the children. If the object is a file,
         * the method is called immediately after prefixWalk_().
         *
         * @param oa the OA of the object currently being traversed. It may not reflect the current
         * state of object attributes if prefixWalk() or walking on siblings or children updates the
         * attributes.
         * @param cookieFromParent the value returned from the {@code prefixWalk_()} on the parent
         * of the current object.
         */
        void postfixWalk_(T cookieFromParent, OA oa) throws Exception;

    }
    /**
     * The class doesn't adapt the prefixWalk method because it doesn't know what to return.
     */
    public static abstract class ObjectWalkerAdapter<T> implements IObjectWalker<T>
    {
        @Override
        public void postfixWalk_(T cookeFromParent, OA oa) throws Exception
        {
        }
    }

    /**
     * Traverse in DFS the directory tree rooted at {@code soid}.
     */
    public <T> void walk_(SOID soid, T cookieFromParent, IObjectWalker<T> w)
        throws Exception
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

    public void setCA_(SOKID sokid, long len, long mtime, ContentHash h, Trans t)
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

        _al.objectModified_(sokid.soid(), resolve_(oa), t);
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
        assert sokid.kidx().equals(KIndex.MASTER) || ret != null;

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
    public @Nullable SOID getSOID_(FID fid) throws SQLException
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
                assert oa.ca(k.kidx()) == null;
                return false;
            } else {
                return oa.ca(k.kidx()) != null;
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

    public String generateNameConflictFileName_(Path pParent, String name)
            throws SQLException, ExNotFound
    {
        do {
            name = Util.newNextFileName(name);
        } while (resolveNullable_(pParent.append(name)) != null);
        return name;
    }
}
