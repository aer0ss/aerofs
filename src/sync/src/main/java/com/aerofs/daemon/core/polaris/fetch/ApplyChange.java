/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

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
     * complexity of the underlying logic and databse schema.
     */
    public interface Impl {
        boolean ackMatchingSubmittedMetaChange_(SIndex sidx, RemoteChange rc, RemoteLink lnk,
                                                       Trans t)
                throws Exception;

        boolean exists_(SOID parent) throws SQLException;

        /**
         * @return true if matching content was already present locally
         */
        boolean newContent_(SOID soid, RemoteChange c, Trans t)
                throws SQLException, IOException;

        void delete_(SOID parent, OID oidChild, Trans t) throws Exception;

        void insert_(SOID parent, OID oidChild, String childName, ObjectType childObjectType,
                     long mergeBoundary, Trans t) throws Exception;

        void move_(SOID parent, OID oidChild, String name,
                   long mergeBoundary, Trans t) throws Exception;

        void applyBufferedChanges_(SIndex sidx, long timestamp, Trans t) throws Exception;
    }

    private final Impl _impl;
    private final CentralVersionDatabase _cvdb;
    private final RemoteLinkDatabase _rpdb;
    private final RemoteContentDatabase _rcdb;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public ApplyChange(Impl impl,  CentralVersionDatabase cvdb, RemoteLinkDatabase rpdb,
            RemoteContentDatabase rcdb, MapSIndex2Store sidx2s)
    {
        _impl = impl;
        _cvdb = cvdb;
        _rpdb = rpdb;
        _rcdb = rcdb;
        _sidx2s = sidx2s;
    }

    public void apply_(SIndex sidx, RemoteChange c, long mergeBoundary, Trans t) throws Exception
    {
        SOID parent = new SOID(sidx, new OID(c.oid));
        if (_impl.exists_(parent)) {
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
        default:
            l.error("unsupported change type {}", c.transformType);
            throw new ExProtocolError();
        }
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
            l.info("ignoring obsolete content change {}:{} {}", soid, version, c.newVersion);
            return;
        }

        if (_impl.newContent_(soid, c, t)) {
            l.info("match local content -> ignore");
            _cvdb.setVersion_(sidx, soid.oid(), c.newVersion, t);
            _rcdb.deleteUpToVersion_(sidx, soid.oid(), c.newVersion, t);
        } else {
            _sidx2s.get_(soid.sidx()).iface(ContentFetcher.class).schedule_(soid.oid(), t);
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

        _rpdb.removeParent_(sidx, oidChild, t);

        _impl.delete_(parent, oidChild, t);
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
        _rpdb.updateParent_(parent.sidx(), oidChild, parent.oid(), name, logicalTimestamp, t);

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

        _impl.insert_(parent, oidChild, c.childName, c.childObjectType, mergeBoundary, t);
    }
}
