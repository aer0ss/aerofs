/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.IOException;
import java.sql.SQLException;

class BlockFolderAdapter implements IPhysicalFolder
{
    private final BlockFolder _blockFolder;
    private final BlockExportedFolder _exportedFolder;

    BlockFolderAdapter(BlockStorage s, SOID soid, Path path, InjectableFile.Factory fileFactory)
    {
        _blockFolder = new BlockFolder(soid, path);
        _exportedFolder = s.exportRoot() != null ?
                new BlockExportedFolder(s, soid, path, fileFactory) : null;
    }

    @Override
    public String toString()
    {
        return "BlockExportedFolder(" + _blockFolder._soid + "," + _blockFolder._path + ")";
    }

    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        _blockFolder.create_(op, t);
        if (exportWritethroughNeededForOp(op)) {
            _exportedFolder.create_(t);
        }
    }

    @Override
    public void delete_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        _blockFolder.delete_(op, t);
        if (exportWritethroughNeededForOp(op)) {
            _exportedFolder.delete_(t);
        }
    }

    @Override
    public void move_(IPhysicalObject to, PhysicalOp op, Trans t) throws IOException, SQLException
    {
        BlockFolderAdapter target = (BlockFolderAdapter)to;
        _blockFolder.move_(target._blockFolder, op, t);
        if (exportWritethroughNeededForOp(op)) {
            _exportedFolder.move_(target._exportedFolder, t);
        }
    }

    private boolean exportWritethroughNeededForOp(PhysicalOp op)
    {
        return _exportedFolder != null && op == PhysicalOp.APPLY;
    }
}
