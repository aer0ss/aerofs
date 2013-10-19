package com.aerofs.daemon.core.phy.linked;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.TransUtil;
import com.aerofs.daemon.core.phy.TransUtil.IPhysicalOperation;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper.PathType;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import org.slf4j.Logger;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;

import com.aerofs.lib.id.SOKID;

public class LinkedFile extends AbstractLinkedObject implements IPhysicalFile
{
    private static final Logger l = Loggers.getLogger(LinkedFile.class);

    final SOKID _sokid;
    private IFIDMaintainer _fidm;

    public LinkedFile(LinkedStorage s, SOKID sokid, LinkedPath path) throws SQLException
    {
        super(s);
        _sokid = sokid;
        setPath(path);
    }

    @Override
    public void setPath(LinkedPath path)
    {
        super.setPath(path);
        _fidm = _s._factFIDMan.create_(_sokid, _f);
    }

    @Override
    SOID soid()
    {
        return _sokid.soid();
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("create {} {}", this, op);

        switch (op) {
        case APPLY:
            _s.try_(this, t, new IPhysicalOperation()
            {
                @Override
                public void run_()
                        throws IOException
                {
                    _f.createNewFile();
                }
            });
            TransUtil.onRollback_(_f, t, new IPhysicalOperation() {
                @Override
                public void run_() throws IOException
                {
                    _f.delete();
                }
            });
            // fallthrough
        case MAP:
            created_(t);
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
    }

    /**
     * Call this method whenever the linked file is created, either by moving an external file
     * into the root anchor, or by actually creating a file in the anchor.
     */
    void created_(Trans t) throws IOException, SQLException
    {
        _fidm.physicalObjectCreated_(t);
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void move_(ResolvedPath path, KIndex kidx, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        LinkedFile lf = _s.newFile_(path, kidx, PathType.Destination);
        l.debug("move {} -> {} {}", this, lf, op);

        switch (op) {
        case APPLY:
            _fidm.throwIfFIDInconsistent_();

            _s.move_(this, lf, t);
            // fallthrough
        case MAP:
            _s.onDeletion_(this, t);
            _fidm.physicalObjectMoved_(lf._fidm, t);
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void delete_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("delete {} {}", this, op);

        switch (op) {
        case APPLY:
            _fidm.throwIfFIDInconsistent_();

            _s.moveToRev_(this, t);
            // fallthrough
        case MAP:
            _s.onDeletion_(this, t);
            _fidm.physicalObjectDeleted_(t);
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
    }

    @Override
    public void updateSOID_(SOID soid, Trans t) throws IOException, SQLException
    {
        l.debug("update {} {} {}", _sokid, soid, _path);
        final String newName;
        SOKID sokid = new SOKID(soid.sidx(), soid.oid(), _sokid.kidx());
        // if the physical filename contains the OID, rename it to use the new canonical OID
        // conflict branches and non-representable objects are the only such cases at this time
        if (!_sokid.kidx().isMaster()) {
            newName = LinkedPath.makeAuxFileName(sokid);
        } else if (!_path.isRepresentable()) {
            newName = LinkedPath.makeAuxFileName(sokid.soid());
        } else {
            return;
        }

        final InjectableFile to = _f.getParentFile().newChild(newName);
        if (to.exists()) throw new IOException("destination already exists");
        TransUtil.moveWithRollback_(_f, to, t);
    }

    @Override
    public long getLength_()
    {
        return _f.getLengthOrZeroIfNotFile();
    }

    @Override
    public long getLastModificationOrCurrentTime_() throws IOException
    {
        return _f.lastModified();
    }

    @Override
    public InputStream newInputStream_() throws IOException
    {
        try {
            return new FileInputStream(_f.getImplementation());
        } catch (FileNotFoundException e) {
            throw new ExFileNotFound(_f.getImplementation());
        }
    }

    @Override
    public boolean wasModifiedSince(long mtime, long len) throws IOException
    {
        return _f.wasModifiedSince(mtime, len);
    }

    @Override
    public String getAbsPath_()
    {
        return _f.getAbsolutePath();
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
