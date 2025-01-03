package com.aerofs.daemon.core.phy.linked;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.phy.linked.LinkedStorage.MoveToRepresentableException;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper.PathType;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.lib.injectable.InjectableFile;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;

public class LinkedFolder extends AbstractLinkedObject implements IPhysicalFolder
{
    private static Logger l = Loggers.getLogger(LinkedFolder.class);

    private final SOID _soid;
    private IFIDMaintainer _fidm;

    public LinkedFolder(LinkedStorage s, SOID soid, LinkedPath path) throws SQLException
    {
        super(s);
        _soid = soid;
        setPath(path);
    }

    @Override
    public void setPath(LinkedPath path)
    {
        super.setPath(path);
        _fidm = _s._factFIDMan.create_(_soid, _f);
    }

    @Override
    SOID soid()
    {
        return _soid;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("create {} {}", this, op);

        switch (op) {
        case APPLY:
            _s._rh.try_(this, t, () ->
                    // NB: this MUST NOT be simplified into a method reference
                    // as the _f field may be updated by try_
                    _f.mkdir());

            TransUtil.onRollback_(_f, t, () -> {
                // delete recursively in case files are created within the
                // folder after it's created and before the rollback
                _f.deleteOrThrowIfExistRecursively();
            });
            // fallthrough
        case MAP:
            _fidm.physicalObjectCreated_(t);
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void move_(ResolvedPath to, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        LinkedFolder lf = _s.newFolder_(to,
                op == PhysicalOp.APPLY ? PathType.DESTINATION : PathType.SOURCE);
        l.debug("move {} -> {} {}", this, lf, op);

        switch (op) {
        case APPLY:
            _fidm.throwIfFIDInconsistent_();

            try {
                _s.move_(this, lf, t);
            } catch (MoveToRepresentableException e) {
                l.warn("failed to make nro representable {}", _soid,
                        BaseLogUtil.suppress(e.getCause()));
                checkState(_soid.equals(lf._soid));
                // no physical changes, skip fallthrough
                break;
            }
            // fallthrough
        case MAP:
            _s.onDeletion_(this, op, t);
            _fidm.physicalObjectMoved_(lf._fidm, t);
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
    }

    @SuppressWarnings("fallthrough")
    @Override
    public @Nullable String delete_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("delete {} {}", this, op);

        // when a folder hierarchy is deleted from the filesystem we will get MAP operations,
        // however they only make sense for representable objects. For non-representable and
        // auxiliary objects, the deletion should be APPLY'ed, otherwise we'll end up with
        // ghost physical objects in the NRO dialog.
        if (!_path.isRepresentable() && op == PhysicalOp.MAP) op = PhysicalOp.APPLY;

        switch (op) {
        case APPLY:
            // If ignored folders get in the system we do not immediately delete the physical
            // objects when the corresponding logical object is deleted. We will however
            // delete ignored children recursively when deleting a non-ignored folder.
            if (_f.exists() && !_s._il.isIgnored(_f.getName())) applyDeletion_(t);
            // fallthrough
        case MAP:
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
        // some NRO update required even when NOP deleting
        _s.onDeletion_(this, op, t);
        // always reset FID to avoid violating FID consistency invariants
        _fidm.physicalObjectDeleted_(t);
        return null;
    }

    private void applyDeletion_(Trans t) throws SQLException, IOException
    {
        deleteIgnoredChildren_();

        // throw if the folder is not empty after ignored children has been deleted, so that we
        // don't accidentally delete non-ignored objects under the folder.
        // NB: if the folder was deleted under our feet we don't throw. This is necessary to
        // avoid a nasty class of crash loops where a shared folder is left on one device and
        // deleted on another *while the daemon is not running*. Because of the delay between
        // detecting a deletion on the filesystem and acting upon it (see TimeoutDeletionBuffer)
        // ACL updates are likely to be processed before the event leaves the buffer and thus to
        // try, and fail, to delete an already deleted folder.

        if (!_path.isInAuxRoot()) {
            _fidm.throwIfFIDInconsistent_();
            _f.deleteOrThrowIfExist();
        } else {
            // non-representable folders cannot contain "non-scanned" objects
            // 1. we *can* safely recursively delete children
            // 2. we *must* recursively delete children when the requested op was MAP
            //    because the caller won't have done it for us (once again we observe
            //    the awkwardness of the whole PhysicalOp concept that not only leaks
            //    implementation behavior in the logical layer but does so in such a
            //    broken way that it often generate subtle bugs.
            // TODO: refactor logical/physical interface as part of upcoming scalability work
            _f.deleteOrThrowIfExistRecursively();
        }

        TransUtil.onRollback_(_f, t, () -> {
            try {
                _f.mkdir();
            } catch (IOException e) {
                // InjectableFile.mkdir throws IOException if File.mkdir returns false
                // That can happen if the directory already exists, which is very much
                // unexpected in this case but not a good enough reason to wreak havoc with
                // a transaction rollback
                if (!(_f.exists() && _f.isDirectory())) {
                    throw new IOException("fs rollback failed: " + _f.exists()
                            + " " + _f.isDirectory(), e);
                }
            }
        });
    }

    @Override
    public void updateSOID_(SOID soid, Trans t) throws IOException, SQLException
    {
        l.debug("update {} {} {}", _soid, soid, _path);

        _s._rh.updateSOID_(_soid, soid, t);

        if (!_path.isRepresentable()) {
            String newName = LinkedPath.makeAuxFileName(soid);
            InjectableFile to = _f.getParentFile().newChild(newName);
            if (_f.exists()) {
                if (to.exists()) throw new IOException("destination exists: " + newName);
                TransUtil.moveWithRollback_(_f, to, t);
            } else if (!to.exists()) {
                if (to.exists()) throw new IOException("source missing: " + _f.getName());
            }
        }
    }

    /**
     * Delete all the objects under "this" folder that are ignored by the ignored list.
     *
     * N.B. This method doesn't back up ignored files. Therefore, they will be irreversibly deleted
     * when the parent folder is deleted.
     */
    private void deleteIgnoredChildren_() throws IOException
    {
        InjectableFile[] children = _f.listFiles();
        if (children != null) {
            for (InjectableFile child : children) {
                if (_s._il.isIgnored(child.getName())) child.deleteOrThrowIfExistRecursively();
            }
        }
    }

    @Override
    public void promoteToAnchor_(SID sid, PhysicalOp op, Trans t) throws IOException, SQLException
    {
        _s.promoteToAnchor_(sid, _path.physical, t);
    }

    @Override
    public void demoteToRegularFolder_(SID sid, PhysicalOp op, Trans t) throws IOException, SQLException
    {
        _s.demoteToRegularFolder_(sid, _path.physical, t);
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
