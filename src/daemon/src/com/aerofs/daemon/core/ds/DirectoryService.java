/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.Dumpables;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.Path;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Set;

public abstract class DirectoryService implements IDumpStatMisc, IPathResolver, IStoreDeletionOperator
{
    public DirectoryService()
    {
        Dumpables.add("ds", this);
    }

    public abstract void addListener_(IDirectoryServiceListener listener);

    public abstract boolean hasChildren_(SOID soid)
            throws SQLException;

    public abstract IDBIterator<OID> listChildren_(SOID soid)
            throws SQLException;

    /**
     * Prefer the iterator version in new code, unless you have a good reason to fetch the
     * whole list of children to memory (e.g. if you want to add/remove children within a loopp
     * over current children)
     */
    public abstract Set<OID> getChildren_(SOID soid)
            throws SQLException;


    public abstract OID getChild_(SIndex sidx, OID parent, String name) throws SQLException;

    /**
     * @return null if the store doesn't exist, which should happen if and only if the anchor
     * is expelled.
     */
    @Nullable public abstract SOID followAnchorNullable_(OA oa);

    /**
     * @throws ExExpelled if the anchor is expelled, i.e. followAnchor_() returns null
     */
    @Nonnull public final SOID followAnchorThrows_(OA oa) throws ExExpelled
    {
        SOID soid = followAnchorNullable_(oa);
        if (soid == null) throw new ExExpelled();
        return soid;
    }

    /**
     * @return the SOID corresponding to the path. Do NOT follow anchor if the path points to an anchor.
     * Return null if not found.
     */
    @Nullable public abstract SOID resolveNullable_(Path path) throws SQLException;

    /**
     * @return the SOID corresponding to the path. Do NOT follow anchor if the path points to an anchor.
     */
    @Nonnull public final SOID resolveThrows_(Path path)
            throws SQLException, ExNotFound
    {
        SOID soid = resolveNullable_(path);
        if (soid == null) throw new ExNotFound();
        return soid;
    }

    /**
     * @return the SOID corresponding to the path. Follow anchor if the path points to an anchor.
     */
    @Nonnull public final SOID resolveFollowAnchorThrows_(Path path)
            throws SQLException, ExNotFound, ExExpelled
    {
        SOID soid = resolveThrows_(path);
        OA oa = getOAThrows_(soid);
        if (oa.isAnchor()) soid = followAnchorThrows_(oa);
        return soid;
    }

    /**
     * @return the SOID corresponding to the path. Do NOT follow anchor if the path points to an anchor.
     */
    @Nonnull public final ResolvedPath resolveThrows_(SOID soid) throws SQLException, ExNotFound
    {
        ResolvedPath ret = resolveNullable_(soid);
        if (ret == null) throw new ExNotFound();
        return ret;
    }

    /**
     * @return the path corresponding to the soid
     */
    @Nonnull public final ResolvedPath resolve_(SOID soid) throws SQLException
    {
        ResolvedPath ret = resolveNullable_(soid);
        assert ret != null : soid;
        return ret;
    }

    /**
     * N.B. an anchor has the same path as the root folder of its anchored store
     * @return null if not found
     */
    @Override
    @Nullable public final ResolvedPath resolveNullable_(SOID soid) throws SQLException
    {
        OA oa = getOANullable_(soid);
        return oa == null ? null : resolve_(oa);
    }

    /**
     * N.B. an anchor has the same path as the root folder of its anchored store
     * @return unlike other versions of resolve(), it never returns null
     */
    @Nonnull public abstract ResolvedPath resolve_(@Nonnull OA oa) throws SQLException;


    @Nonnull public final OA getOAThrows_(SOID soid)
            throws ExNotFound, SQLException
    {
        OA oa = getOANullable_(soid);
        if (oa == null) throw new ExNotFound("" + soid.toString());
        return oa;
    }

    /**
     * @return whether there exists an OA for soid; the object is assumed to be a target
     */
    public final boolean hasOA_(SOID soid) throws SQLException
    {
        return getOANullable_(soid) != null;
    }

    /**
     * @return whether there exists an OA for soid; the object can be an alias or a target
     */
    public final boolean hasAliasedOA_(SOID soid) throws SQLException
    {
        return getAliasedOANullable_(soid) != null;
    }

    /**
     * DO NOT USE outside of LogicalStagingArea
     *
     * To ensure that the use of the staging area does not change externally observable behavior
     * while still allowing it to be leveraged for incremental cleanup of entire stores we have to
     * filter out objects lookup.
     *
     * If a store is not locally admitted getOANullable_ will return null for any object within
     * that store, even though the object may still be present in the DB. However, in the staging
     * area itself we neeed to bypass this filter but we still want to go through DirectoryService
     * to leverage caching and to trigger listeners as needed.
     */
    @Nullable public abstract OA getOANullableNoFilter_(SOID soid) throws SQLException;

    /**
     * @return null if not found.
     */
    @Nullable public abstract OA getOANullable_(SOID soid) throws SQLException;

    /**
     * TODO (MJ) OA's are stale after performing any directory service write for oa.soid(), but
     * there is no safety check to warn devs of this fact.
     */
    @Nonnull public abstract OA getOA_(SOID soid) throws SQLException;

    /**
     * @return object attribute including aliased ones.
     */
    @Nullable public abstract OA getAliasedOANullable_(SOID soid) throws SQLException;

    /**
     * N.B. should be called by HdCreateObject only
     * @throws ExNotFound if the parent is not found
     */
    public abstract void createOA_(Type type, SIndex sidx, OID oid, OID oidParent, String name,
            Trans t) throws ExAlreadyExist, ExNotFound, SQLException;

    public abstract void createCA_(SOID soid, KIndex kidx, Trans t) throws SQLException;

    public abstract void deleteCA_(SOID soid, KIndex kidx, Trans t) throws SQLException;

    /**
     * @return true if the object is under a trash folder
     *
     * This method is final because it would be a pain in the ass to mock
     */
    public final boolean isDeleted_(@Nonnull OA oa) throws SQLException
    {
        SIndex sidx = oa.soid().sidx();
        while (!oa.parent().isRoot() && !oa.parent().isTrash()) {
            oa = getOA_(new SOID(sidx, oa.parent()));
        }
        return oa.parent().isTrash();
    }

    public final boolean isTrashOrDeleted_(@Nonnull SOID soid) throws SQLException
    {
        return soid.oid().isTrash() || isDeleted_(getOA_(soid));
    }

    /**
     * N.B. should be called by ObjectMovement only
     */
    public abstract void setOAParentAndName_(@Nonnull OA oa, @Nonnull OA oaParent, String name, Trans t)
            throws SQLException, ExAlreadyExist, ExNotDir;

    public abstract void setExpelled_(SOID soid, boolean expelled, Trans t)
            throws SQLException;

    /**
     * The callback interface for walk_()
     *
     * Tread with care when using walk as it may be an expensive operation and there
     * is currently no way to predict how many object it will touch.
     *
     * Also be aware that walking across expulsion and store boundaries may lead to
     * unexpected behavior, especially if incremental cleanup is ongoing.
     *
     * See {@link com.aerofs.daemon.core.expel.LogicalStagingArea} for details.
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
                throws Exception;

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
                throws Exception;

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
    public final <T> void walk_(SOID soid, @Nullable T cookieFromParent, IObjectWalker<T> w)
            throws Exception
    {
        // 1. walk_ is used in LogicalStagingArea
        // 2. other places that walk_ stop at expulsion or store boundary
        OA oa = getOANullableNoFilter_(soid);
        if (oa == null) throw new ExNotFound("" + soid);

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

        // make sure a long-running DirectoryService.walk_ doesn't cause the daemon to be killed
        ProgressIndicators.get().incrementMonotonicProgress();
    }

    public abstract void unsetFID_(SOID soid, Trans t) throws SQLException;

    public abstract void setFID_(SOID soid, @Nonnull FID fid, Trans t) throws SQLException;

    /**
     * Assign the specified object a randomly generated FID which is not used by other objects
     *
     * NB: use a random UUID to avoid conflicts with real FIDs
     */
    public final void randomizeFID_(SOID soid, Trans t) throws SQLException
    {
        FID fid = new FID(UniqueID.generate().getBytes());
        setFID_(soid, fid, t);
    }

    /**
     * @pre the CA must already exists
     */
    public abstract void setCA_(SOKID sokid, long len, long mtime, @Nullable ContentHash h, Trans t)
            throws SQLException;

    /**
     * Because fetching hashes from the db is expensive, we don't make the hash part of the CA
     * class. Instead, we fetch hashes only when needed.
     */
    public abstract ContentHash getCAHash_(SOKID sokid) throws SQLException;

    public abstract void setCAHash_(SOKID sokid, @Nonnull ContentHash h, Trans t) throws SQLException;

    /**
     * @return null if not found
     */
    @Nullable public abstract SOID getSOIDNullable_(FID fid) throws SQLException;

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
            return !oa.isExpelled() && oa.caNullable(k.kidx()) != null;
        }
    }

    /**
     * If no conflict exists, then returns the original name provided
     */
    public final String generateConflictFreeFileName_(@Nonnull Path pParent, String name)
            throws SQLException, ExNotFound
    {
        while (resolveNullable_(pParent.append(name)) != null) {
            name = Util.nextFileName(name);
        }
        return name;
    }

    public abstract IDBIterator<SOKID> getAllNonMasterBranches_() throws SQLException;

    public abstract long getBytesUsed_(SIndex sidx) throws SQLException;
}
