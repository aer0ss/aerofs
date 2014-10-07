package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.notification.ISnapshotableNotificationEmitter;
import com.aerofs.daemon.core.notification.Notifications;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.phy.TransUtil.IPhysicalOperation;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper class that abstracts out most of the complexity of dealing with Non-Representable Objects
 *
 * See docs/design/filesystem_restrictions.md for a high-level overview
 */
public class RepresentabilityHelper implements ISnapshotableNotificationEmitter
{
    private final static Logger l = Loggers.getLogger(RepresentabilityHelper.class);

    public static class DestinationAlreadyExistsException extends IOException {
        private static final long serialVersionUID = 0L;
    }

    private static class NoFIDException extends Exception {
        private static final long serialVersionUID = 0L;
        NoFIDException() { super(); }
        NoFIDException(Exception e) { super(e); }
    }

    private final IOSUtil _os;
    private final LinkerRootMap _lrm;
    private final InjectableDriver _dr;
    private final IMetaDatabase _mdb;
    final NRODatabase _nrodb;
    private final InjectableFile.Factory _factFile;
    private final RitualNotificationServer _rns;
    private final DirectoryService _ds;

    private final Map<SID, Integer> _nroCount = Maps.newHashMap();

    @Inject
    public RepresentabilityHelper(IOSUtil os, InjectableDriver dr, LinkerRootMap lrm,
            IMetaDatabase mdb, NRODatabase nrodb, InjectableFile.Factory factFile,
            RitualNotificationServer rns, DirectoryService ds)
    {
        _os = os;
        _dr = dr;
        _lrm = lrm;
        _mdb = mdb;
        _nrodb = nrodb;
        _factFile = factFile;
        _rns = rns;
        _ds = ds;
    }

    public enum Representability
    {
        REPRESENTABLE,
        INHERENT_NRO,
        CONTEXTUAL_NRO
    }


    public Representability representability_(OA oa) throws SQLException
    {
        if (_os.isInvalidFileName(oa.name())) return Representability.INHERENT_NRO;
        return _nrodb.isNonRepresentable_(oa.soid()) ? Representability.CONTEXTUAL_NRO : Representability.REPRESENTABLE;
    }

    public boolean isNonRepresentable_(OA oa) throws SQLException
    {
        return representability_(oa) != Representability.REPRESENTABLE;
    }

    enum PathType
    {
        SOURCE,         // the path is assumed to be occupied by an existing object
        DESTINATION     // the path is assumed to be free so that an object may be moved there
    }

    /**
     * Derive the physical path corresponding to a given virtual path
     *
     * Non-Representable objects are stored in the NRO auxiliary folder with their SOID as name
     * to avoid conflicts.
     *
     * To correctly handle children of non-representable folders we need to operate on a fully
     * resolved path (i.e. with SOIDs for each path component).
     *
     * NOTE: source and destination path need to be computed slightly differently. specifically,
     * for a destination the SOID of the {@link ResolvedPath} is NOT checked against the
     * {@link NRODatabase}. This is necessary to prevent NROs from getting "stuck". Without this,
     * moving an NRO to a representable path would not trigger automatic representability update.
     */
    LinkedPath getPhysicalPath_(ResolvedPath path, PathType type) throws SQLException
    {
        String s = "";
        String[] elems = path.elements();

        // iterate upwards over path components to find the first non-representable object, if any
        for (int i = elems.length - 1; i >= 0; --i) {
            String component = elems[i];
            boolean nro = (type == PathType.SOURCE || i < elems.length - 1)
                    && _nrodb.isNonRepresentable_(path.soids.get(i));
            if (nro || _os.isInvalidFileName(component)) {
                // reached first NRO
                String base = _lrm.auxFilePath_(path.sid(), path.soids.get(i),
                        AuxFolder.NON_REPRESENTABLE);
                return s.isEmpty()
                        ? LinkedPath.nonRepresentable(path, base)
                        : LinkedPath.representableChildOfNonRepresentable(path, Util.join(base, s));
            } else {
                s = s.isEmpty() ? component : Util.join(component, s);
            }
        }

        return LinkedPath.representable(path, _lrm.absRootAnchor_(path.sid()));
    }

    /**
     * When running into an NRO during a scan, it may be safe and desirable in some corner cases
     * to simply mark it as representable and keep going.
     *
     * @pre #representability_ returned CONTEXTUAL_NRO for the given object
     * @return whether the given object was marked as representable
     */
    public boolean markRepresentable_(SID sid, OA oa, Trans t) throws SQLException
    {
        SOID soid = oa.soid();
        String absPath = _lrm.auxFilePath_(sid, soid, AuxFolder.NON_REPRESENTABLE);
        if (_factFile.create(absPath).exists()) return false;
        if (_nrodb.getConflict_(soid) != null) return false;
        _nrodb.setRepresentable_(soid, t);
        return true;
    }

    /**
     * Whenever a transaction touches NROs, count number of NROs when it is committed and
     * send emit a Ritual notification with that information
     */
    private final TransLocal<Set<SID>> _tlNotify = new TransLocal<Set<SID>>() {
        @Override
        protected Set<SID> initialValue(Trans t)
        {
            final Set<SID> s = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    // update NRO counter for affected physical roots
                    for (SID sid : s) updateCount_(sid);
                    sendSnapshot_();
                }
            });
            return s;
        }
    };

    @Override
    public void sendSnapshot_()
    {
        int total = 0;
        for (LinkerRoot r : _lrm.getAllRoots_()) {
            // initialize NRO counter the first time a notification is sent
            if (_nroCount.get(r.sid()) == null) {
                updateCount_(r.sid());
            }
            total += _nroCount.get(r.sid());
        }
        _rns.getRitualNotifier()
                .sendNotification(Notifications.newNROCountNotification(total));
    }

    private static class NROCounter implements Function<SOID, Void> {
        int n = 0;
        @Override
        public @Nullable Void apply(@Nullable SOID soid)
        {
            ++n;
            return null;
        }
    }

    private void updateCount_(SID sid)
    {
        NROCounter c = new NROCounter();
        forNonRepresentableObjects_(sid, c);
        _nroCount.put(sid, c.n);
    }

    void forNonRepresentableObjects_(SID sid, Function<SOID, Void> c)
    {
        String absPath = Util.join(_lrm.auxRoot_(sid), AuxFolder.NON_REPRESENTABLE._name);
        String[] children = _factFile.create(absPath).list();
        if (children == null) return;
        for (String child : children) {
            SOID soid = LinkedPath.soidFromFileNameNullable(child);
            // guard against invalid files messing up the count
            if (soid == null) continue;
            try {
                OA oa = _ds.getOANullable_(soid);
                if (oa != null && isNonRepresentable_(oa) && !oa.isExpelled()) c.apply(soid);
            } catch (SQLException e) {
                l.error("could not determine nro status {}", soid, e);
            }
        }
    }

    void updateSOID_(SOID oldSOID, SOID newSOID, Trans t) throws SQLException
    {
        if (oldSOID.sidx().equals(newSOID.sidx())) {
            // aliasing
            l.debug("update oid {} {}", oldSOID, newSOID.oid());
            _nrodb.updateOID_(oldSOID, newSOID.oid(), t);
            _nrodb.updateConflicts_(oldSOID, newSOID.oid(), t);
        } else if (oldSOID.oid().equals(newSOID.oid())) {
            // migration
            l.debug("update sidx {} {}", oldSOID, newSOID.sidx());
            _nrodb.updateSIndex_(oldSOID, newSOID.sidx(), t);
        } else {
            // anchor demotion (migration)
            l.debug("update soid {} {}", oldSOID, newSOID);
            _nrodb.updateOID_(oldSOID, newSOID.oid(), t);
            _nrodb.updateConflicts_(oldSOID, newSOID.oid(), t);
            _nrodb.updateSIndex_(new SOID(oldSOID.sidx(), newSOID.oid()), newSOID.sidx(), t);
        }
    }

    /**
     * Attemps to perform an operation (create, move, ...) on a given destination object
     * and work around filesystem limitations as required by converting
     *
     * @pre destination is expected NOT to exist
     */
    void try_(AbstractLinkedObject dest, Trans t, IPhysicalOperation op)
            throws SQLException, IOException
    {
        if (!dest._path.isRepresentable()) {
            l.debug("op on nro");
            op.run_();
            if (dest._path.virtual != null) _tlNotify.get(t).add(dest._path.virtual.sid());
        } else if (isContextuallyNonRepresentable_(dest)) {
            l.info("make cnro eagerly {}", dest);
            markNonRepresentable_(dest, t);
            op.run_();
        } else {
            try {
                op.run_();
            } catch (IOException e) {
                l.info("make cnro lazily: {}", dest, e);
                markNonRepresentable_(dest, t);
                op.run_();
            }
        }
    }

    /**
     * Test whether an object is contextually non-representable
     *
     * @pre destination is expected NOT to exist
     */
    private boolean isContextuallyNonRepresentable_(AbstractLinkedObject dest)
            throws SQLException, IOException
    {
        if (!dest._f.exists()) return false;

        // dest exists: does it correspond to a known object?
        try {
            SOID soid = soidAtPath_(dest._path.physical);

            // race condition: we want scanner to pick up file before we overwrite it
            if (soid == null) {
                l.warn("dest already exists {}", dest);
                throw new DestinationAlreadyExistsException();
            }

            // existing object: we have a path conflict
            return !soid.equals(dest.soid());
        } catch (NoFIDException e) {
            // lack of perm, os-specific file or other weirdness: treat as NRO
            l.warn("could not determine fid for {}", dest);
            return true;
        }
    }

    private @Nullable SOID soidAtPath_(String physicalPath)
            throws NoFIDException, SQLException
    {
        FID fid;
        try {
            fid = _dr.getFID(physicalPath);
        } catch (Exception e) {
            l.warn("could not determine FID for {}", physicalPath, e);
            throw new NoFIDException(e);
        }
        if (fid == null) {
            l.warn("could not determine FID for {}", physicalPath);
            throw new NoFIDException();
        }
        return _mdb.getSOID_(fid);
    }

    private void markNonRepresentable_(AbstractLinkedObject dest, Trans t) throws SQLException
    {
        checkNotNull(dest._path.virtual);
        addNonRepresentableObject_(dest, t);
        dest.markNonRepresentable();
    }

    private void addNonRepresentableObject_(AbstractLinkedObject dest, Trans t) throws SQLException
    {
        SOID conflict = null;
        if (dest._f.exists()) {
            // conflicting representations, determine conflicting object
            try {
                conflict = soidAtPath_(dest._path.physical);
            } catch (NoFIDException e) {
                l.warn("treat as inro {}", dest);
                // treat as inherently non-representable
            }
        }
        _nrodb.setNonRepresentable_(dest.soid(), conflict, t);
        if (dest._path.virtual != null) _tlNotify.get(t).add(dest._path.virtual.sid());
    }

    /**
     * React to deletion of a physical object
     *
     * If the object was representable we may need to pick a new-winner among the
     * contextually non-representable objects that conflicted with the deleted object.
     *
     * On the other hand, if the object was non-representable we need to update the NRO db.
     *
     * @param o deleted physical object
     */
    void updateNonRepresentableObjectsOnDeletion_(AbstractLinkedObject o, PhysicalOp op, Trans t)
            throws SQLException
    {
        if (o._path.isRepresentable()) {
            // do not attempt to pick a new winner upon NOP deletion
            if (op == PhysicalOp.NOP) return;
            // do not attempt to pick a new winner if the physical spot is still occupied
            if (o._f.exists()) return;
            updateConflictsOnDeletion_(o.soid(), o._path, t);
        } else if (o._path.isNonRepresentable()) {
            _nrodb.setRepresentable_(o.soid(), t);
            if (o._path.virtual != null) _tlNotify.get(t).add(o._path.virtual.sid());
        }
    }

    /**
     * Whenever an object is deleted it is necessary to check whether it conflicted with
     * some other objects (and caused them to be marked as Non-Representable).
     * If so, we pick one of the conflicting objects as the new "winner", mark it as representable
     * and update the NRO table accordingly.
     *
     * @param soid SOID of deleted/moved object
     * @param path path of deleted/moved object
     */
    private void updateConflictsOnDeletion_(final SOID soid, final LinkedPath path, Trans t)
            throws SQLException
    {
        checkNotNull(path.virtual);
        SID sid = path.virtual.sid();
        IDBIterator<SOID> it = _nrodb.getConflicts_(soid);
        try {
            while (it.next_()) {
                SOID winner = it.get_();
                OA oa = _ds.getOA_(winner);
                InjectableFile source = _factFile.create(
                        _lrm.auxFilePath_(sid, winner, AuxFolder.NON_REPRESENTABLE));
                InjectableFile dest = _factFile.create(
                        Util.join(new File(path.physical).getParent(), oa.name()));

                try {
                    // try move object to its rightful parent
                    TransUtil.moveWithRollback_(source, dest, t);
                } catch (IOException e) {
                    // NB: we swallow the error on purpose. However unlikely it may be, failure to
                    // automatically pick a new "winner" should under no circumstance prevent the
                    // original operation from succeeding.
                    l.error("Failed to auto-update conflict {}:{} {}", soid, winner, e.getMessage());
                    // try next conflicting object
                    continue;
                }

                // mark object as representable
                _nrodb.setRepresentable_(winner, t);
                _tlNotify.get(t).add(path.virtual.sid());

                // if more conflicting objects are left, update conflict column in NRO table
                if (it.next_()) _nrodb.updateConflicts_(soid, winner.oid(), t);
                break;
            }
        } finally {
            it.close_();
        }
    }

    /**
     * @return the OID of another object that is in conflict with the object identified by
     * {@paramref soid}, null if the object with SOID {@paramref soid} is not in conflict with any
     * other objects.
     */
    public @Nullable OID getConflict_(SOID soid) throws SQLException
    {
        return _nrodb.getConflict_(soid);
    }
}
