package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOKID;

import java.io.IOException;
import java.sql.SQLException;

public class TestLinkedFolder extends AbstractTestLinkedObject<IPhysicalFolder>
{
    @Override
    protected IPhysicalFolder createPhysicalObject(LinkedStorage s, SOKID sokid, LinkedPath path)
            throws SQLException
    {
        return new LinkedFolder(s, sokid.soid(), path);
    }

    @Override
    protected void move(IPhysicalObject obj,
            ResolvedPath path, SOKID sokid, PhysicalOp op, Trans t) throws IOException, SQLException
    {
        ((LinkedFolder)obj).move_(path, op, t);
    }
}
