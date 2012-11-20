/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser.migration;

import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SOID;

import java.io.IOException;
import java.sql.SQLException;

public class NullImmigrantCreator implements IImmigrantCreator
{
    @Override
    public SOID createImmigrantRecursively_(SOID soidFromRoot, SOID soidToRootParent,
            String toRootName, PhysicalOp op, Trans t)
            throws ExStreamInvalid, IOException, ExNotFound, ExAlreadyExist, SQLException, ExNotDir
    {
        throw SystemUtil.fatalWithReturn("not implemented");
    }
}
