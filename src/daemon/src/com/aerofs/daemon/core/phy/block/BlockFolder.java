/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOID;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

class BlockFolder implements IPhysicalFolder
{
    private static final Logger l = Util.l(BlockFolder.class);

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
        if (l.isDebugEnabled()) l.debug(this + ".create_(" + op + ")");
        // Noop: we do not need to maintain any explicit folder structure
    }

    @Override
    public void delete_(PhysicalOp op, Trans t) throws IOException, SQLException
    {
        if (l.isDebugEnabled()) l.debug(this + ".delete_(" + op + ")");
        // Noop: we do not need to maintain any explicit folder structure
    }

    @Override
    public void move_(IPhysicalObject to, PhysicalOp op, Trans t) throws IOException, SQLException
    {
        if (l.isDebugEnabled()) l.debug(this + ".move_(" + to + ", " + op + ")");
        // Noop: we do not need to maintain any explicit folder structure
    }
}
