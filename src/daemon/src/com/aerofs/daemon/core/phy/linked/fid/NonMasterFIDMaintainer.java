package com.aerofs.daemon.core.phy.linked.fid;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;

/**
 * This class maintains the FID values for non-master branches of a file.
 */
public class NonMasterFIDMaintainer implements IFIDMaintainer
{
    @Override
    public void throwIfFIDInconsistent_() throws IOException, SQLException
    {
        // noop
    }

    @Override
    public void physicalObjectCreated_(Trans t)
    {
        // No-op since we don't store in the DB the FID of the physical files that correspond to
        // non-master branches.
    }

    @Override
    public void physicalObjectMoved_(IFIDMaintainer to, Trans t) throws SQLException, IOException
    {
        if (to instanceof NonMasterFIDMaintainer) {
            // no-op
        } else {
            assert to instanceof MasterFIDMaintainer;
            MasterFIDMaintainer fidmTo = (MasterFIDMaintainer) to;
            fidmTo.setFIDFromFilesystem_(t);
        }
    }

    @Override
    public void physicalObjectDeleted_(Trans t)
    {
        // no-op
    }
}
