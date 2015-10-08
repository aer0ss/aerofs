/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

class BlockFolder implements IPhysicalFolder
{
    private static final Logger l = Loggers.getLogger(BlockFolder.class);

    final SOID _soid;
    final Path _path;

    BlockFolder(SOID soid, Path path)
    {
        _soid = soid;
        _path = path;
    }

    @Override
    public String toString()
    {
        return "BlockFolder(" + _soid + "," + _path + ")";
    }

    @Override
    public void create_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("{}.create_({})", this, op);
        // Noop: we do not need to maintain any explicit folder structure
    }

    @Override
    public @Nullable String delete_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("{}.delete_({})", this, op);
        // Noop: we do not need to maintain any explicit folder structure
        return null;
    }

    @Override
    public void move_(ResolvedPath to, PhysicalOp op, Trans t) throws IOException, SQLException
    {
        l.debug("{}.move_({}, {})", this, to, op);
        // Noop: we do not need to maintain any explicit folder structure
    }

    @Override
    public void updateSOID_(SOID soid, Trans t) throws IOException, SQLException
    {
        l.debug("{}.alias_({})", soid);
        // Noop: we do not need to maintain any explicit folder structure
    }

    @Override
    public void promoteToAnchor_(SID sid, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {}

    @Override
    public void demoteToRegularFolder_(SID anchor, PhysicalOp op, Trans t)
            throws IOException, SQLException
    {}
}
