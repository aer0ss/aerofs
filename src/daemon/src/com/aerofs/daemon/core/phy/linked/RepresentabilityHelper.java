package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.phy.TransUtil.IPhysicalOperation;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Helper class that abstracts out most of the complexity of dealing with Non-Representable Objects
 *
 * See docs/design/filesystem_restricitons.md for a high-level overview
 */
public class RepresentabilityHelper
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
    private final NRODatabase _nrodb;
    private final InjectableFile.Factory _factFile;

    @Inject
    public RepresentabilityHelper(IOSUtil os, InjectableDriver dr, LinkerRootMap lrm,
            IMetaDatabase mdb, NRODatabase nrodb, InjectableFile.Factory factFile)
    {
        _os = os;
        _dr = dr;
        _lrm = lrm;
        _mdb = mdb;
        _nrodb = nrodb;
        _factFile = factFile;
    }

    public boolean isNonRepresentable(OA oa) throws SQLException
    {
        return _os.isInvalidFileName(oa.name()) || _nrodb.isNonRepresentable_(oa.soid());
    }

    enum PathType
    {
        Source,         // the path is assumed to be occupied by an existing object
        Destination     // the path is assumed to be free so that an object may be moved there
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
    LinkedPath physicalPath(ResolvedPath path, PathType type) throws SQLException
    {
        String s = "";
        String[] elems = path.elements();

        // iterate upwards over path components to find the first non-representable object, if any
        for (int i = elems.length - 1; i >= 0; --i) {
            String component = elems[i];
            boolean nro = (type == PathType.Source || i < elems.length - 1)
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
        } else if (isContextuallyNonRepresentable_(dest)) {
            l.info("make cnro eagerly {}", dest);
            markNonRepresentable_(dest, t);
            op.run_();
        } else {
            try {
                op.run_();
            } catch (IOException e) {
                l.info("make cnro lazily: {} {}", dest, e.getCause());
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
        Preconditions.checkNotNull(dest._path.virtual);
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
    void updateNonRepresentableObjectsOnDeletion_(AbstractLinkedObject o, Trans t) throws SQLException
    {
        if (o._path.isRepresentable()) {
            // do not attempt to pick a new winner if the physical spot is still occupied
            if (o._f.exists()) return;
            updateConflictsOnDeletion_(o.soid(), o._path, t);
        } else if (o._path.isNonRepresentable()) {
            _nrodb.setRepresentable_(o.soid(), t);
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
        Preconditions.checkNotNull(path.virtual);
        SID sid = path.virtual.sid();
        IDBIterator<SOID> it = _nrodb.getConflicts_(soid);
        try {
            while (it.next_()) {
                SOID winner = it.get_();
                OA oa = Preconditions.checkNotNull(_mdb.getOA_(winner));
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
    public @Nullable OID conflict(SOID soid) throws SQLException
    {
        return _nrodb.getConflict_(soid);
    }
}
