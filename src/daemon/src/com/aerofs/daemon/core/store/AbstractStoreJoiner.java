/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Join/leave a store upon ACL changes, with multiplicity-specific behavior
 *
 * TODO: find a better name...
 *
 * See {@link com.aerofs.daemon.core.acl.ACLSynchronizer}
 */
public abstract class AbstractStoreJoiner
{
    protected final static Logger l = Loggers.getLogger(AbstractStoreJoiner.class);
    protected final ObjectCreator _oc;
    protected final ObjectSurgeon _os;
    protected final DirectoryService _ds;
    protected final ObjectDeleter _od;

    public AbstractStoreJoiner(DirectoryService ds, ObjectSurgeon os, ObjectCreator oc,
            ObjectDeleter od)
    {
        _ds = ds;
        _os = os;
        _oc = oc;
        _od = od;
    }

    /**
     * Create logical/physical objects as needed when gaining access to a store.
     */
    public abstract void joinStore_(SIndex sidx, SID sid, String folderName, boolean external, Trans t) throws Exception;

    /**
     * Remove logical/physical objects as needed when losing access to a store.
     */
    public abstract void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception;

    /**
     * Create/remove anchors as needed on TS when member list changes
     */
    public void adjustAnchors_(SIndex sidx, String folderName, Set<UserID> newMembers, Trans t)
            throws Exception
    {}

    /**
     * Creating an anchor when joining a store is a complex operation
     *
     * This method is meant to be used by subclasses and NOT meant to be reimplemented.
     */
    protected void createAnchorIfNeeded_(SIndex sidx, SID sid, String folderName, SIndex root, Trans t)
            throws Exception
    {
        /**
         * If the original folder is already present in the root store we should not auto-join but
         * instead wait for the conversion to propagate. We do not check for its presence in other
         * stores as some migration scenarios could lead us to wrongly ignore explicit joins (e.g
         * if a subfolder of a shared folder is moved up to the root store, shared and joined
         * before the original move propagates)
         */
        OID oid = SID.convertedStoreSID2folderOID(sid);
        OA oaFolder = _ds.getOANullable_(new SOID(root, oid));
        if (oaFolder != null && !_ds.isDeleted_(oaFolder)) {
            l.info("original folder already present {} {}", sidx, sid);
            return;
        }

        checkArgument(folderName != null, "Unnamed shared folder", sidx, sid.toStringFormal());

        SOID anchor = new SOID(root, SID.storeSID2anchorOID(sid));
        OA oaAnchor = _ds.getOANullable_(anchor);

        /**
         * By default, we do not increment version vector on purpose to avoid false conflicts
         * between multiple devices of the same user joining the shared folder
         */
        boolean updateVersion = false;

        /*
         * If we receive the ACL update after HdShareFolder successfully migrates the folder,
         * ACLSynchronizer will detect we were granted access to a new store and call this method
         * but we don't need to do anything as HdShareFolder already created the new store.
         *
         * Similarly, if we get an anchor creation message from the sharer and successfully dl the
         * anchor before receiving the ACL update we don't have anything to do.
         *
         * NB: we could conceivably move this check to ACLSynchronizer but that would make it
         * dependent on DirectoryService which is probably a bad idea...
         */
        if (oaAnchor != null) {
            assert oaAnchor.isAnchor() : anchor;
            if (_ds.isDeleted_(oaAnchor)) {
                l.info("restore deleted anchor {} {}", sidx, anchor);
                /**
                 * If the user was kicked out and later re-invited the anchor will be present in its
                 * trash. Moving the anchor out of the trash would be too cumbersome so we simply
                 * delete the existing OA and proceed to re-create it as if it had never existed
                 */
                _os.deleteOA_(anchor, t);

                /**
                 * when restoring a deleted anchor we are forced to update the version
                 */
                updateVersion = true;
            } else {
                l.info("anchor already present {} {}", sidx, anchor);
                return;
            }
        }

        l.info("anchoring: {} {} {}", sidx, folderName, anchor);

        while (true) {
            try {
                _oc.createMeta_(Type.ANCHOR, anchor, OID.ROOT, folderName, 0, PhysicalOp.APPLY,
                        false, updateVersion, t);
                break;
            } catch (ExAlreadyExist e) {
                l.warn("duplicate", e);
                folderName = Util.nextFileName(folderName);
            }
        }

        l.info("anchored {} as {}", anchor, folderName);
    }

    /**
     * When a user loses access to a store, any locally present anchor is deleted.
     *
     * This method is meant to be used by subclasses and NOT meant to be reimplemented.
     */
    protected Path deleteAnchorIfNeeded_(SIndex sidx, SID sid, Trans t) throws Exception
    {
        SOID soid = new SOID(sidx, SID.storeSID2anchorOID(sid));
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return null;

        checkState(oa.type() == Type.ANCHOR);

        final ResolvedPath path = _ds.resolve_(oa);
        if (path.isInTrash()) return null;
        l.info("deleting anchor {} {}", sidx, sid);
        _od.delete_(oa.soid(), PhysicalOp.APPLY, t);

        return path;
    }
}
