/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase.BufferedChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;


/**
 * This class is responsible for applying metadata changes received from Polaris.
 *
 * Changes are applied in-order. Polaris may not filter out changes that originated from the local
 * device, it is therefore necessary to distinguish between true and false conflicts.
 *
 * TODO: encapsulate DS/PS/Expulsion manipulation in separate class
 */
public class ApplyChange
{
    private final static Logger l = Loggers.getLogger(ApplyChange.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final CentralVersionDatabase _cvdb;
    private final RemoteLinkDatabase _rpdb;
    private final MapAlias2Target _a2t;
    private final ObjectSurgeon _os;
    private final MetaBufferDatabase _mbdb;
    private final MetaChangesDatabase _mcdb;
    private final MetaChangeSubmitter _submitter;
    private final ContentChangesDatabase _ccdb;
    private final RemoteContentDatabase _rcdb;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public ApplyChange(DirectoryService ds, IPhysicalStorage ps, Expulsion expulsion,
            CentralVersionDatabase cvdb, RemoteLinkDatabase rpdb, MapAlias2Target a2t,
            ObjectSurgeon os, MetaBufferDatabase mbdb, MetaChangesDatabase mcdb,
            MetaChangeSubmitter submitter, ContentChangesDatabase ccdb, RemoteContentDatabase rcdb,
            MapSIndex2Store sidx2s)
    {
        _ds = ds;
        _ps = ps;
        _expulsion = expulsion;
        _cvdb = cvdb;
        _rpdb = rpdb;
        _a2t = a2t;
        _os = os;
        _mbdb = mbdb;
        _mcdb = mcdb;
        _submitter = submitter;
        _ccdb = ccdb;
        _rcdb = rcdb;
        _sidx2s = sidx2s;
    }

    public void apply_(SIndex sidx, RemoteChange c, long mergeBoundary, Trans t) throws Exception
    {
        SOID parent = new SOID(sidx, new OID(c.oid));
        if (_ds.getOANullable_(parent) == null && !_mbdb.isBuffered_(parent)) {
            l.error("dafuq {} {}", sidx, parent);
            throw new ExProtocolError();
        }

        if (c.transformType == RemoteChange.Type.UPDATE_CONTENT) {
            applyContentChange_(parent, c ,t);
            return;
        }

        // the value of version indicate that we have applied *ALL* changes up to that point
        // so any incoming changes that predate this value can be safely ignored
        Long version = _cvdb.getVersion_(sidx, parent.oid());
        if (version != null && version >= c.newVersion) {
            l.info("ignoring obsolete change {}:{} {}", parent, version, c.newVersion);
            return;
        }

        l.info("apply[{}] {} {} {}: {} {} {}",
                sidx, c.logicalTimestamp, c.oid, c.newVersion, c.transformType, c.child, c.childName);

        OID child = c.child;
        RemoteLink lnk = _rpdb.getParent_(sidx, child);

        // A link is a (parent, child) pair
        // "remote" links reflect local knowledge of the linearized state of Polaris
        // The version of a link is the last version  of the parent at which the link was changed
        //
        // To avoid false-conflicts to be detected when receiving a change that originated from the
        // local device and no longer represent the current state on the device (as a result of a
        // series of moves for instance) we:
        //   1. update links not just when applying remote changes but also when successfully
        //     submitting local changes
        //   2. ignore remote changes that are demonstrably obsolete based on link versions
        //
        // NB: this logic will break down if Polaris ever implements log compaction for series of
        // moves across different parents
        //
        // NB: an alternate way of preventing false conflicts would be for Polaris to tag each
        // transform with the originating DID and to filter out changes originating from the
        // calling devices in the /changes route
        // This approach would have the side benefit of providing a centralized persistent audit
        // trail.
        if (lnk != null && lnk.logicalTimestamp >= c.logicalTimestamp) {
            l.info("ignoring obsolete change {}:{} {}:{} {}",
                    parent, version, lnk.parent, lnk.logicalTimestamp, c.newVersion);
            return;
        }

        // we need to detect the case where we submitted a meta change and receive the resulting
        // transform when fetching changes *BEFORE* receiving an ACK.
        // In such a case we want to simply
        if (_submitter.ackMatchingSubmittedMetaChange_(sidx, c, lnk, t)) {
            return;
        }

        _cvdb.setVersion_(sidx, parent.oid(), c.newVersion, t);
        switch (c.transformType) {
        case INSERT_CHILD:
            insertChild(parent, c, mergeBoundary, t);
            break;
        case RENAME_CHILD:
            renameChild(parent, c, mergeBoundary, t);
            break;
        case REMOVE_CHILD:
            removeChild(parent, c, lnk, t);
            break;
        default:
            l.error("unsupported change type {}", c.transformType);
            throw new ExProtocolError();
        }
    }

    private void applyContentChange_(SOID soid, RemoteChange c, Trans t)
            throws SQLException, IOException
    {
        SIndex sidx = soid.sidx();

        Long version = _rcdb.getMaxVersion_(sidx, soid.oid());
        if (version != null && version >= c.newVersion) {
            l.info("ignoring obsolete content change {}:{} {}", soid, version, c.newVersion);
            return;
        }

        OA oa = _ds.getOANullable_(soid);
        if (oa != null && !oa.isExpelled()) {
            ContentHash h = _ds.getCAHash_(new SOKID(soid, KIndex.MASTER));
            if (h != null && h.equals(c.contentHash)
                    && oa.caMaster().length() == c.contentSize) {
                l.info("match local content -> ignore");
                _ccdb.deleteChange_(sidx, soid.oid(), t);
                _cvdb.setVersion_(sidx, soid.oid(), c.newVersion, t);
                _rcdb.deleteUpToVersion_(sidx, soid.oid(), c.newVersion, t);
                // add "remote" content entry for latest version (in case of expulsion)
                _rcdb.insert_(sidx, soid.oid(), c.newVersion, new DID(c.originator),
                        c.contentHash, c.contentSize, t);

                // delete conflict branch if any
                if (oa.cas().size() > 1) {
                    checkState(oa.cas().size() == 2);
                    KIndex kidx = KIndex.MASTER.increment();
                    l.info("delete branch {}k{}", soid, kidx);
                    _ds.deleteCA_(soid, kidx, t);
                    _ps.newFile_(_ds.resolve_(oa), kidx).delete_(PhysicalOp.APPLY, t);
                }
                return;
            }
        }
        // NB: it's safe to apply content change immediately even if the creation of the
        // file is still in the meta buffer. The content fetcher will simply ignore the entry
        // until the OA is created. If the object end up being deleted when buffered changes
        // are applied then either LogicalStagingArea or ContentFetcherIterator will clean up
        // the db as required.
        _rcdb.insert_(sidx, soid.oid(), c.newVersion, new DID(c.originator), c.contentHash,
                c.contentSize, t);
        if (oa == null || !oa.isExpelled()) {
            _sidx2s.get_(sidx).contentFetcher().schedule_(soid.oid(), t);
        }
    }

    private static OA.Type type(ObjectType t) throws ExProtocolError
    {
        switch (t) {
        case FILE:
            return Type.FILE;
        case FOLDER:
            return Type.DIR;
        case MOUNT_POINT:
            return Type.ANCHOR;
        default:
            throw new ExProtocolError();
        }
    }

    private void removeChild(SOID parent, RemoteChange c, RemoteLink lnk, Trans t)
            throws Exception
    {
        SIndex sidx = parent.sidx();
        OID oidChild = c.child;

        /**
         * MOVE_CHILD is converted by Polaris into an INSERT_CHILD followed by a REMOVE_CHILD
         *
         * The INSERT_CHILD is guaranteed to be received first and will update RemoteParentDatabase
         * to point to the new parent. This can be used to detect whether a subsequent REMOVE_CHILD
         * can be safely ignored.
         */
        OID remoteParent = lnk != null ? lnk.parent : null;
        if (!parent.oid().equals(remoteParent)) {
            l.debug("ignore REMOVE_CHILD {}/{} {}", parent, oidChild, remoteParent);
            return;
        }

        SOID soidChild = new SOID(sidx, oidChild);

        boolean isBuffered = _mbdb.isBuffered_(soidChild);

        _rpdb.removeParent_(sidx, oidChild, t);

        // defer delete
        if (isBuffered) return;

        OA oaChild = _ds.getOANullable_(soidChild);
        if (oaChild == null) {
            l.warn("no such object to remove {} {}", parent, oidChild);
        } else {
            applyDelete_(oaChild, t);
        }
    }

    private void renameChild(SOID parent, RemoteChange c, long mergeBoundary, Trans t)
            throws Exception
    {
        moveChild(parent, c.child, c.childName, c.logicalTimestamp, mergeBoundary, t);
    }

    private void moveChild(SOID parent, OID oidChild, String name, long logicalTimestamp,
            long mergeBoundary, Trans t)
            throws Exception
    {
        SIndex sidx = parent.sidx();
        SOID soidChild = new SOID(sidx, oidChild);

        // buffer moves if:
        //   1. the child is already buffered
        //   2. a name conflict is detected
        boolean isBuffered = _mbdb.isBuffered_(soidChild);
        boolean hasConflict = _ds.getChild_(sidx, parent.oid(), name) != null;

        _rpdb.updateParent_(sidx, oidChild, parent.oid(), name, logicalTimestamp, t);

        if (isBuffered) return;

        OA oaChild = _ds.getOA_(soidChild);
        if (hasConflict) {
            _mbdb.insert_(sidx, oidChild, oaChild.type(), mergeBoundary, t);
        } else {
            applyMove_(_ds.getOA_(parent), oaChild, name, t);
        }
    }

    private void insertChild(SOID parent, RemoteChange c, long mergeBoundary, Trans t)
            throws Exception
    {
        SIndex sidx = parent.sidx();
        OID oidChild = c.child;
        Type type = type(c.childObjectType);

        /**
         * MOVE_CHILD is converted by Polaris into an INSERT_CHILD followed by a REMOVE_CHILD
         *
         * The INSERT_CHILD is guaranteed to be received first. When receiving an INSERT_CHILD,
         * checking the RemoteParentDatabase for the new child tells us whether it should be
         * turned into a MOVE
         */
        OA oaChild = _ds.getOANullable_(new SOID(sidx, oidChild));
        if (oaChild != null || _rpdb.getParent_(sidx, oidChild) != null) {
            checkState(oaChild == null || type == oaChild.type());
            // TODO(phoenix): handle cycle creation
            moveChild(parent, oidChild, c.childName, c.logicalTimestamp, mergeBoundary, t);
            return;
        }

        // buffer inserts if:
        //   1. there are local changes
        //   2. the parent is already buffered
        //   3. a name conflict is detected
        boolean shouldBuffer = _mbdb.isBuffered_(parent) || _mcdb.hasChanges_(sidx)
                || _ds.getChild_(sidx, parent.oid(), c.childName) != null;

        _rpdb.insertParent_(sidx, oidChild, parent.oid(), c.childName, c.logicalTimestamp, t);

        if (shouldBuffer) {
            _mbdb.insert_(sidx, oidChild, type, mergeBoundary, t);
        } else {
            applyInsert_(sidx, oidChild, parent.oid(), c.childName, type, t);
        }
    }

    private void applyInsert_(SIndex sidx, OID oidChild, OID oidParent, String name, Type type,
            Trans t) throws Exception
    {
        _ds.createOA_(type, sidx, oidChild, oidParent, name, t);
        OA oa = _ds.getOA_(new SOID(sidx, oidChild));
        if (!oa.isExpelled() && oa.isDirOrAnchor()) {
            _ps.newFolder_(_ds.resolve_(new SOID(sidx, oidChild))).create_(PhysicalOp.APPLY, t);
        }
    }

    private void applyMove_(OA oaParent, OA oaChild, String name, Trans t) throws Exception
    {
        ResolvedPath pOld = _ds.resolve_(oaChild);
        _ds.setOAParentAndName_(oaChild, oaParent, name, t);

        _expulsion.objectMoved_(pOld, oaChild.soid(), PhysicalOp.APPLY, t);

        // all local meta changes affecting the same child are now obsolete
        _mcdb.deleteChanges_(oaChild.soid().sidx(), oaChild.soid().oid(), t);
    }

    void applyDelete_(OA oaChild, Trans t) throws Exception
    {
        OA oaTrash = _ds.getOA_(new SOID(oaChild.soid().sidx(), OID.TRASH));
        applyMove_(oaTrash, oaChild, oaChild.soid().oid().toStringFormal(), t);
    }

    public void applyBufferedChanges_(SIndex sidx, long timestamp, Trans t)
            throws Exception
    {
        BufferedChange c;
        while ((c = _mbdb.getBufferedChange_(sidx, timestamp)) != null) {
            applyBufferedChange_(sidx, c, t);
        }
    }

    private void applyBufferedChange_(SIndex sidx, BufferedChange c, Trans t) throws Exception
    {
        RemoteLink lnk = _rpdb.getParent_(sidx, c.oid);
        OA oaChild = _ds.getOANullable_(new SOID(sidx, c.oid));

        // mark change as applied immediately because early returns
        _mbdb.remove_(sidx, c.oid, t);

        if (lnk == null) {
            if (oaChild == null) {
                applyInsert_(sidx, c.oid, OID.TRASH, c.oid.toStringFormal(), c.type, t);
            } else {
                applyDelete_(oaChild, t);
            }
            return;
        }

        // ensure parent exists
        OA oaParent = _ds.getOANullable_(new SOID(sidx, lnk.parent));
        if (oaParent == null) {
            l.info("apply parent {}{}", sidx, lnk.parent);
            checkState(_mbdb.isBuffered_(new SOID(sidx, lnk.parent)));
            // TODO(phoenix): watch for cycles
            applyBufferedChange_(sidx, new BufferedChange(lnk.parent, Type.DIR), t);
            oaParent = _ds.getOA_(new SOID(sidx, lnk.parent));
        }

        String name = lnk.name;

        // check for conflict
        OID oidConflict = _ds.getChild_(sidx, lnk.parent, name);
        if (oidConflict != null) {
            RemoteLink clnk = _rpdb.getParent_(sidx, oidConflict);
            OA oaConflict = _ds.getOA_(new SOID(sidx, oidConflict));

            // TODO(phoenix): should NOT allow aliasing if local change submitted but not ack'ed
            // instead should throw and let the submitter perform aliasing on 409
            if (oaChild == null && clnk == null
                    && oaConflict.type() == c.type&& c.type != Type.ANCHOR) {
                alias_(sidx, c.oid, oaConflict, t);

                // no need to insert the child
                return;
            } else  {
                Long cv = clnk != null ? clnk.logicalTimestamp : null;
                name = resolveNameConflict_(sidx, lnk, oaConflict, cv, t);
            }
        }

        // apply remote change
        if (oaChild == null) {
            applyInsert_(sidx, c.oid, lnk.parent, name, c.type, t);
        } else {
            applyMove_(oaParent, oaChild, name, t);
        }
    }

    private String resolveNameConflict_(SIndex sidx, RemoteLink lnk, OA oaConflict, Long cv, Trans t)
            throws Exception
    {
        String targetName = nextNonConflictingName_(sidx, lnk.parent, lnk.name);

        if (cv != null && cv >= lnk.logicalTimestamp) {
            l.info("rename remote {}", targetName);
            return targetName;
        }

        l.info("rename local {} {}", oaConflict.soid(), targetName);

        OA oaParent = _ds.getOA_(new SOID(sidx, lnk.parent));
        ResolvedPath pConflict = _ds.resolve_(oaConflict);
        _ds.setOAParentAndName_(oaConflict, oaParent, targetName, t);

        _expulsion.objectMoved_(pConflict, oaConflict.soid(), PhysicalOp.APPLY, t);

        _mcdb.insertChange_(sidx, oaConflict.soid().oid(), lnk.parent, targetName, t);

        return lnk.name;
    }

    private String nextNonConflictingName_(SIndex sidx, OID parent, String name) throws SQLException
    {
        // TODO: binary search
        String targetName = name;
        do {
            targetName = Util.nextFileName(targetName);
        } while (_ds.getChild_(sidx, parent, targetName) != null);
        return targetName;
    }

    private void alias_(SIndex sidx, OID oid, OA oaConflict, Trans t) throws Exception
    {
        l.info("alias {} {} {}", sidx, oid, oaConflict.soid().oid());

        ResolvedPath pConflict = _ds.resolve_(oaConflict);
        _os.replaceOID_(oaConflict.soid(), new SOID(sidx, oid), t);

        _mcdb.deleteChanges_(sidx, oaConflict.soid().oid(), t);

        // TODO: expire alias entries (use local meta change idx)
        // any meta change made after the alias event will use the new OID
        // the only place where the mapping of old oid to new OID is needed in when submitting
        // meta changes
        _a2t.add_(oaConflict.soid(), new SOID(sidx, oid), t);

        if (oaConflict.type() == Type.FILE) {
            // expect no conflict branch since alias are always local-only
            checkState(oaConflict.cas().size() <= 1);
            if (oaConflict.caMasterNullable() != null) {
                _ps.newFile_(pConflict, KIndex.MASTER).updateSOID_(new SOID(sidx, oid), t);
            }
            _ps.deletePrefix_(new SOKID(oaConflict.soid(), KIndex.MASTER));
            if (_ccdb.deleteChange_(sidx, oaConflict.soid().oid(), t)) {
                _ccdb.insertChange_(sidx, oid, t);
            }
        } else {
            _ps.newFolder_(pConflict).updateSOID_(new SOID(sidx, oid), t);
        }
    }
}
