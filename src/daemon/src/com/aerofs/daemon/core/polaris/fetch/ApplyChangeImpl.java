package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
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

public class ApplyChangeImpl implements ApplyChange.Impl
{
    private static final Logger l = Loggers.getLogger(ApplyChangeImpl.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final MapAlias2Target _a2t;
    private final ObjectSurgeon _os;
    private final RemoteLinkDatabase _rpdb;
    private final MetaBufferDatabase _mbdb;
    private final MetaChangesDatabase _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final MetaChangeSubmitter _submitter;

    @Inject
    public ApplyChangeImpl(DirectoryService ds, IPhysicalStorage ps, Expulsion expulsion,
                           MapAlias2Target a2t, ObjectSurgeon os, RemoteLinkDatabase rpdb,
                           MetaBufferDatabase mbdb, MetaChangesDatabase mcdb,
                           ContentChangesDatabase ccdb, MetaChangeSubmitter submitter)
    {
        _ds = ds;
        _ps = ps;
        _expulsion = expulsion;
        _a2t = a2t;
        _os = os;
        _rpdb = rpdb;
        _mbdb = mbdb;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _submitter = submitter;
    }

    @Override
    public boolean ackMatchingSubmittedMetaChange_(SIndex sidx, RemoteChange rc, RemoteLink lnk,
                                            Trans t) throws Exception
    {
        return _submitter.ackMatchingSubmittedMetaChange_(sidx, rc, lnk, t);
    }

    private static OA.Type type(ObjectType t) throws ExProtocolError {
        switch (t) {
            case FILE:
                return OA.Type.FILE;
            case FOLDER:
                return OA.Type.DIR;
            case MOUNT_POINT:
                return OA.Type.ANCHOR;
            default:
                throw new ExProtocolError();
        }
    }

    @Override
    public boolean exists_(SOID soid) throws SQLException {
        return _ds.getOANullable_(soid) == null && !_mbdb.isBuffered_(soid);
    }

    @Override
    public boolean newContent_(SOID soid, RemoteChange c, Trans t)
            throws SQLException, IOException {
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return false;
        CA ca = oa.caMasterNullable();
        if (ca == null) return false;
        ContentHash h = _ds.getCAHash_(new SOKID(soid, KIndex.MASTER));
        if (h == null || !h.equals(c.contentHash) || ca.length() != c.contentSize) return false;

        // local content matches remote change
        // -> discard local change if any
        _ccdb.deleteChange_(soid.sidx(), soid.oid(), t);

        // -> delete conflict branch if any
        if (oa.cas().size() > 1) {
            checkState(oa.cas().size() == 2);
            KIndex kidx = KIndex.MASTER.increment();
            l.info("delete branch {}k{}", soid, kidx);
            _ds.deleteCA_(soid, kidx, t);
            _ps.newFile_(_ds.resolve_(oa), kidx).delete_(PhysicalOp.APPLY, t);
        }
        return true;
    }

    @Override
    public void insert_(SOID parent, OID oidChild, String name, ObjectType objectType,
                 long mergeBoundary, Trans t) throws Exception {
        OA.Type type = type(objectType);
        // buffer inserts if:
        //   1. there are local changes
        //   2. the parent is already buffered
        //   3. a name conflict is detected
        boolean shouldBuffer = _mbdb.isBuffered_(parent) || _mcdb.hasChanges_(parent.sidx())
                || _ds.getChild_(parent.sidx(), parent.oid(), name) != null;

        if (shouldBuffer) {
            _mbdb.insert_(parent.sidx(), oidChild, type, mergeBoundary, t);
        } else {
            applyInsert_(parent.sidx(), oidChild, parent.oid(), name, type, t);
        }
    }

    private void applyInsert_(SIndex sidx, OID oidChild, OID oidParent, String name, OA.Type type,
                              Trans t) throws Exception {
        OA oa = _ds.getOANullable_(new SOID(sidx, oidChild));
        if (oa != null) {
            checkState(oa.type() == type, "%s<%s>/<%s>", sidx, oidParent, oidChild);
            l.info("ignore insert for existing object {} {} {}", sidx, oidParent, oidChild);
            return;
        }
        _ds.createOA_(type, sidx, oidChild, oidParent, name, t);
        oa = _ds.getOA_(new SOID(sidx, oidChild));
        if (!oa.isExpelled() && oa.isDirOrAnchor()) {
            _ps.newFolder_(_ds.resolve_(new SOID(sidx, oidChild))).create_(PhysicalOp.APPLY, t);
        }
    }

    @Override
    public void move_(SOID parent, OID oidChild, String name, long mergeBoundary,
               Trans t) throws Exception {
        SIndex sidx = parent.sidx();
        SOID soidChild = new SOID(parent.sidx(), oidChild);
        // buffer moves if:
        //   1. the child is already buffered
        //   2. a name conflict is detected
        boolean isBuffered = _mbdb.isBuffered_(soidChild);
        boolean hasConflict = _ds.getChild_(sidx, parent.oid(), name) != null;

        if (isBuffered) return;

        OA oaChild = _ds.getOA_(soidChild);
        if (hasConflict) {
            _mbdb.insert_(sidx, oidChild, oaChild.type(), mergeBoundary, t);
        } else {
            applyMove_(_ds.getOA_(parent), oaChild, name, t);
        }
    }

    private void applyMove_(OA oaParent, OA oaChild, String name, Trans t) throws Exception {
        ResolvedPath pOld = _ds.resolve_(oaChild);
        _ds.setOAParentAndName_(oaChild, oaParent, name, t);

        _expulsion.objectMoved_(pOld, oaChild.soid(), PhysicalOp.APPLY, t);

        // all local meta changes affecting the same child are now obsolete
        _mcdb.deleteChanges_(oaChild.soid().sidx(), oaChild.soid().oid(), t);
    }

    @Override
    public void delete_(SOID parent, OID oidChild, Trans t) throws Exception {
        SOID soidChild = new SOID(parent.sidx(), oidChild);

        boolean isBuffered = _mbdb.isBuffered_(soidChild);

        // defer delete
        if (isBuffered) return;

        OA oaChild = _ds.getOANullable_(soidChild);
        if (oaChild == null) {
            l.warn("no such object to remove {} {}", parent, oidChild);
        } else {
            applyDelete_(oaChild, t);
        }
    }

    void applyDelete_(OA oaChild, Trans t) throws Exception {
        OA oaTrash = _ds.getOA_(new SOID(oaChild.soid().sidx(), OID.TRASH));
        applyMove_(oaTrash, oaChild, oaChild.soid().oid().toStringFormal(), t);
    }

    public void applyBufferedChanges_(SIndex sidx, long timestamp, Trans t)
            throws Exception {
        MetaBufferDatabase.BufferedChange c;
        while ((c = _mbdb.getBufferedChange_(sidx, timestamp)) != null) {
            applyBufferedChange_(sidx, c, t);
        }
    }

    private void applyBufferedChange_(SIndex sidx, MetaBufferDatabase.BufferedChange c, Trans t) throws Exception {
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
            applyBufferedChange_(sidx, new MetaBufferDatabase.BufferedChange(lnk.parent, OA.Type.DIR), t);
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
                    && oaConflict.type() == c.type && c.type != OA.Type.ANCHOR) {
                alias_(sidx, c.oid, oaConflict, t);

                // no need to insert the child
                return;
            } else {
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
            throws Exception {
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

    private String nextNonConflictingName_(SIndex sidx, OID parent, String name) throws SQLException {
        // TODO: binary search
        String targetName = name;
        do {
            targetName = Util.nextFileName(targetName);
        } while (_ds.getChild_(sidx, parent, targetName) != null);
        return targetName;
    }

    private void alias_(SIndex sidx, OID oid, OA oaConflict, Trans t) throws Exception {
        l.info("alias {} {} {}", sidx, oid, oaConflict.soid().oid());

        ResolvedPath pConflict = _ds.resolve_(oaConflict);
        _os.replaceOID_(oaConflict.soid(), new SOID(sidx, oid), t);

        _mcdb.deleteChanges_(sidx, oaConflict.soid().oid(), t);

        // TODO: expire alias entries (use local meta change idx)
        // any meta change made after the alias event will use the new OID
        // the only place where the mapping of old oid to new OID is needed in when submitting
        // meta changes
        _a2t.add_(oaConflict.soid(), new SOID(sidx, oid), t);

        if (oaConflict.type() == OA.Type.FILE) {
            // expect no conflict branch since alias are always local-only
            checkState(oaConflict.cas().size() <= 1);
            if (oaConflict.caMasterNullable() != null) {
                _ps.newFile_(pConflict, KIndex.MASTER).updateSOID_(new SOID(sidx, oid), t);
            }
            _ps.newPrefix_(new SOKID(oaConflict.soid(), KIndex.MASTER), null).delete_();
            if (_ccdb.deleteChange_(sidx, oaConflict.soid().oid(), t)) {
                _ccdb.insertChange_(sidx, oid, t);
            }
        } else {
            _ps.newFolder_(pConflict).updateSOID_(new SOID(sidx, oid), t);
        }
    }
}
