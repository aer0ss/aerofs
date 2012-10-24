package com.aerofs.daemon.core.phy.linked;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FrequentDefectSender;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.LinkedStorage.IRollbackHandler;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import static com.aerofs.lib.Util.join;
import com.aerofs.lib.C.AuxFolder;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.Path;

public class LinkedFile implements IPhysicalFile
{
    private static final Logger l = Util.l(LinkedFile.class);
    private static final FrequentDefectSender fds = new FrequentDefectSender();

    private final LinkedStorage _s;
    private final IFIDMaintainer _fidm;

    final InjectableFile _f;
    final Path _path;
    final SOKID _sokid;

    public LinkedFile(CfgAbsRootAnchor cfgAbsRootAnchor, InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan, LinkedStorage s, SOKID sokid, Path path,
            String pathAuxRoot)
    {
        _s = s;
        _path = path;
        _sokid = sokid;
        _f = factFile.create(sokid.kidx().equals(KIndex.MASTER) ?
                join(cfgAbsRootAnchor.get(), join(path.elements())) :
                join(pathAuxRoot, AuxFolder.CONFLICT._name, LinkedStorage.makeAuxFileName(sokid)));
        _fidm = factFIDMan.create_(sokid, _f);
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        if (l.isDebugEnabled()) l.debug("create " + this);

        switch (op) {
        case APPLY:
            // for files this is only actually called via ObjectCreator for FSIFile and some test cases
            _f.createNewFile();
            LinkedStorage.onRollback_(_f, t, new IRollbackHandler() {
                @Override
                public void rollback_() throws IOException
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
    public void move_(IPhysicalObject po, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        if (l.isDebugEnabled()) l.debug("move " + this + " -> " + po);

        LinkedFile lf = (LinkedFile) po;

        switch (op) {
        case APPLY:
            LinkedStorage.moveWithRollback_(_f, lf._f, t);
            // fallthrough
        case MAP:
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
        if (l.isDebugEnabled()) l.debug("delete " + this);

        switch (op) {
        case APPLY:
            _s.moveToRev_(this, t);
            // fallthrough
        case MAP:
            _fidm.physicalObjectDeleted_(t);
            break;
        default:
            assert op == PhysicalOp.NOP;
        }
    }

    @Override
    public long getLength_()
    {
        return _f.getLengthOrZeroIfNotFile();
    }

    @Override
    public ContentHash getHash_()
    {
        return null;
    }

    @Override
    public long getLastModificationOrCurrentTime_() throws IOException
    {
        return _f.lastModified();
    }

    @Override
    public InputStream newInputStream_() throws FileNotFoundException
    {
        try {
            return new FileInputStream(_f.getImplementation());
        } catch (FileNotFoundException e) {
            Exception ex = new Exception("LF no file " +
                    FileUtil.debugString(_f.getImplementation()), e);
            fds.logSendAsync("LF no file", ex);
            throw e;
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
