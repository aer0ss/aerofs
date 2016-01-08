/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.PolarisContentVersionControl;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;


/**
 * This class is responsible for applying metadata changes received from Polaris.
 *
 * Changes are applied in-order. Polaris may not filter out changes that originated from the local
 * device, it is therefore necessary to distinguish between true and false conflicts.
 */
public class ApplyChange
{
    private final static Logger l = Loggers.getLogger(ApplyChange.class);

    /**
     * The underlying logic will be significantly different between a regular desktop client
     * and a storage agent.
     *
     * This mostly arises from the fact that regular client may have local changes that conflict
     * with changes already accepted by Polaris and therefore need to be resolved.
     *
     * The Storage Agent is never a source of local changes, which allows a drastic reduction in
     * complexity of the underlying logic and database schema.
     */
    public interface Impl {
        boolean ackMatchingSubmittedMetaChange_(SIndex sidx, RemoteChange rc, RemoteLink lnk,
                                                       Trans t)
                throws Exception;

        /**
         * @return true if matching content was already present locally
         */
        boolean hasMatchingLocalContent_(SOID soid, RemoteChange c, Trans t)
                throws SQLException, IOException;

        void delete_(SOID parent, OID oidChild, @Nullable OID migrant, Trans t) throws Exception;

        void insert_(SOID parent, OID oidChild, String childName, ObjectType childObjectType,
                     @Nullable OID migrant, long mergeBoundary, Trans t) throws Exception;

        void move_(SOID parent, OID oidChild, String name,
                   long mergeBoundary, Trans t) throws Exception;

        void share_(SOID soid, Trans t) throws Exception;

        void applyBufferedChanges_(SIndex sidx, long timestamp, Trans t) throws Exception;

        boolean hasBufferedChanges_(SIndex sidx) throws SQLException;
    }

    private final Impl _impl;
    private final ChangeEpochDatabase _cedb;
    private final CentralVersionDatabase _cvdb;
    private final RemoteLinkDatabase _rpdb;
    private final RemoteContentDatabase _rcdb;
    private final IMapSIndex2SID _sidx2sid;
    private final PolarisContentVersionControl _cvc;
    private final ContentFetchQueueDatabase _cfqdb;

    @Inject
    public ApplyChange(Impl impl,  CentralVersionDatabase cvdb, RemoteLinkDatabase rpdb,
            RemoteContentDatabase rcdb, IMapSIndex2SID sidx2sid, ChangeEpochDatabase cedb,
            PolarisContentVersionControl cvc, ContentFetchQueueDatabase cfqdb)
    {
        _impl = impl;
        _cvdb = cvdb;
        _rpdb = rpdb;
        _rcdb = rcdb;
        _cedb = cedb;
        _sidx2sid = sidx2sid;
        _cvc = cvc;
        _cfqdb = cfqdb;
    }

    public void apply_(SIndex sidx, RemoteChange c, long mergeBoundary, Trans t) throws Exception
    {
        SOID parent = new SOID(sidx, new OID(c.oid));
        if (!parent.oid().isRoot() && _rpdb.getParent_(sidx, parent.oid()) == null) {
            l.warn("transform on missing or deleted object {}", parent);
            return;
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
        RemoteLink lnk = c.child != null ? _rpdb.getParent_(sidx, child) : null;

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
        if (_impl.ackMatchingSubmittedMetaChange_(sidx, c, lnk, t)) {
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
        case SHARE:
            share(parent, c.logicalTimestamp, t);
            break;
        default:
            l.error("unsupported change type {}", c.transformType);
            throw new ExProtocolError();
        }
    }

    public boolean hasBufferedChanges_(SIndex sidx) throws SQLException {
        return _impl.hasBufferedChanges_(sidx);
    }

    public void applyBufferedChanges_(SIndex sidx, long timestamp, Trans t) throws Exception{
        _impl.applyBufferedChanges_(sidx, timestamp, t);
    }

    private void applyContentChange_(SOID soid, RemoteChange c, Trans t)
            throws SQLException, IOException
    {
        SIndex sidx = soid.sidx();

        Long version = _rcdb.getMaxVersion_(sidx, soid.oid());
        if (version != null && version >= c.newVersion) {
            // If the local version is an exact match, we need to update Bloom Filters and
            // the content change epoch used to tag them to make sure remote peers become aware
            // that we have this content
            // We do this for "obsolete" changes because of the peculiar way migration interferes
            // with the normal version lifecycle (specifically by preserving versions from the
            // source store in the destination store to preserve happens-before relationship
            // and avoid false conflicts from arising as a result of cross-store moves).
            // These migrated contents cannot be placed in the bloom filter at migration time for
            // two reason:
            //  1. even if the OID doesn't change (sharing) the daemon doesn't and cannot know what
            //     logical timestamp will be assigned to the UPDATE_CONTENT transform by polaris
            //     and thus cannot correctly tag the BF, which would risk causing premature BF
            //     disposal and in turn no-sync
            //  2. if the OID changes the daemon must wait for aliasing to be resolved before it can
            //     add the correct OID to the BF
            Long lv = _cvdb.getVersion_(sidx, soid.oid());
            if (lv != null && lv == c.newVersion) {
                _cvc.setContentVersion_(sidx, soid.oid(), c.newVersion, c.logicalTimestamp, t);
            }
            l.info("ignoring obsolete content change {}:{} {}", soid, version, c.newVersion);
            return;
        }

        long ep = Objects.firstNonNull(_cedb.getContentChangeEpoch_(sidx), 0L);
        _cedb.setContentChangeEpoch_(sidx, Math.max(ep, c.logicalTimestamp), t);

        if (_impl.hasMatchingLocalContent_(soid, c, t)) {
            l.info("match local content {} {}", soid, c.newVersion);
            _cvc.setContentVersion_(sidx, soid.oid(), c.newVersion, c.logicalTimestamp, t);
            _rcdb.deleteUpToVersion_(sidx, soid.oid(), c.newVersion, t);
        } else {
            _cfqdb.insert_(sidx, soid.oid(), t);
        }

        _rcdb.insert_(sidx, soid.oid(), c.newVersion, new DID(c.originator), c.contentHash,
                c.contentSize, t);
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
            l.info("ignore REMOVE_CHILD {}/{} {}", parent, oidChild, remoteParent);
            return;
        }

        // Remove parent after deleting since we use RemoteLinkDatabase to resolve path in SA.
        _impl.delete_(parent, oidChild, c.migrantOid, t);
        _rpdb.removeParent_(sidx, oidChild, t);
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
        RemoteLink lnk = _rpdb.getParent_(parent.sidx(), oidChild);
        if (lnk == null) {
            l.warn("ignore rename for deleted {} {}/{}", oidChild, parent, name);
            return;
        }

        _rpdb.updateParent_(parent.sidx(), oidChild, parent.oid(), name, logicalTimestamp, t);

        // no-op: preserve local changes
        if (lnk.parent.equals(parent.oid()) && lnk.name.equals(name)) {
            l.info("no-op move {} -> {}/{}", oidChild, parent, name);
            return;
        }

        _impl.move_(parent, oidChild, name, mergeBoundary, t);
    }

    private void insertChild(SOID parent, RemoteChange c, long mergeBoundary, Trans t)
            throws Exception
    {
        SIndex sidx = parent.sidx();
        OID oidChild = c.child;

        /**
         * MOVE_CHILD is converted by Polaris into an INSERT_CHILD followed by a REMOVE_CHILD
         *
         * The INSERT_CHILD is guaranteed to be received first. When receiving an INSERT_CHILD,
         * checking the RemoteParentDatabase for the new child tells us whether it should be
         * turned into a MOVE
         */
        if (_rpdb.getParent_(sidx, oidChild) != null) {
            // TODO(phoenix): handle cycle creation
            moveChild(parent, oidChild, c.childName, c.logicalTimestamp, mergeBoundary, t);
            return;
        }

        _rpdb.insertParent_(sidx, oidChild, parent.oid(), c.childName, c.logicalTimestamp, t);

        _impl.insert_(parent, oidChild, c.childName, c.childObjectType, c.migrantOid, mergeBoundary, t);
    }

    private void share(SOID soid, long lts, Trans t) throws Exception
    {
        RemoteLink lnk = _rpdb.getParent_(soid.sidx(), soid.oid());
        if (lnk == null) throw new ExProtocolError("cannot share non-existent folder");

        SID sid = _sidx2sid.get_(soid.sidx());
        if (!sid.isUserRoot()) throw new ExProtocolError("nested sharing not supported");

        // apply all buffered changes in the source store before performing migration
        applyBufferedChanges_(soid.sidx(), Long.MAX_VALUE, t);

        _rpdb.removeParent_(soid.sidx(), soid.oid(), t);

        OID anchor = SID.folderOID2convertedAnchorOID(soid.oid());
        RemoteLink alnk = _rpdb.getParent_(soid.sidx(), anchor);
        if (alnk != null) {
            // anchor already exists. Should only happen after unlink/reinstall wherein shared
            // folders are restored from tag files and the local "changes" are submitted before
            // remote changes are fetched. In that case, the remote parent entry will either match
            // the one implied by the SHARE transform, or be more up-to-date
            if (!alnk.parent.equals(lnk.parent) || !alnk.name.equals(lnk.name)) {
                l.info("racy racy anchor {}: {} vs {}", anchor, lnk, alnk);
            }
        } else {
            _rpdb.insertParent_(soid.sidx(), anchor, lnk.parent, lnk.name, lts, t);
        }

        try {
            _impl.share_(soid, t);
        } catch (Exception e) {
            l.warn("share failed", e);
            throw e;
        }
    }
}
