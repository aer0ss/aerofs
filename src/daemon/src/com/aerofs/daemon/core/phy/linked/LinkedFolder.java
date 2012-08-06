package com.aerofs.daemon.core.phy.linked;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.LinkedStorage.IRollbackHandler;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

public class LinkedFolder implements IPhysicalFolder
{
    private static Logger l = Util.l(LinkedFolder.class);

    private final InjectableFile _f;
    private final IFIDMaintainer _fidm;
    private final IgnoreList _il;

    public LinkedFolder(CfgAbsRootAnchor cfgAbsRootAnchor, InjectableFile.Factory factFile,
            IFIDMaintainer.Factory factFIDMan, IgnoreList il, SOID soid, Path path)
    {
        _f = factFile.create(Util.join(cfgAbsRootAnchor.get(), Util.join(path.elements())));
        _fidm = factFIDMan.create_(soid, _f);
        _il = il;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        if (l.isInfoEnabled()) l.info("create " + this + " " + op);

        switch (op) {
        case APPLY:
            _f.mkdir();
            LinkedStorage.onRollback_(_f, t, new IRollbackHandler() {
                @Override
                public void rollback_() throws IOException
                {
                    // delete recursively in case files are created within the
                    // folder after it's created and before the rollback
                    _f.deleteOrThrowIfExistRecursively();
                }
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
    public void move_(IPhysicalObject po, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {
        if (l.isInfoEnabled()) l.info("move " + this + " -> " + po);

        LinkedFolder lf = (LinkedFolder) po;

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
        if (l.isInfoEnabled()) l.info("delete " + this);

        switch (op) {
        case APPLY:
            deleteIgnoredChildren_();

            // throw if the folder is not empty after ignored children has been deleted, so that we
            // don't accidentally delete non-ignored objects under the folder.
            _f.delete();

            LinkedStorage.onRollback_(_f, t, new IRollbackHandler() {
                @Override
                public void rollback_() throws IOException
                {
                    _f.mkdir();
                }
            });
            // fallthrough
        case MAP:
            _fidm.physicalObjectDeleted_(t);
            break;
        default:
            assert op == PhysicalOp.NOP;
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
                if (_il.isIgnored_(child.getName())) child.deleteOrThrowIfExistRecursively();
            }
        }
    }

    @Override
    public String toString()
    {
        return _f.toString();
    }
}
