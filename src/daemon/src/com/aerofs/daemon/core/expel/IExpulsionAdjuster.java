package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SOID;

import java.io.IOException;
import java.sql.SQLException;

/**
 * After moving an object or changing its expulsion flags, the expulsion flags of its child
 * objects need to be adjusted accordingly. Their physical objects also need to be moved,
 * deleted, or recreated depending on the situation.
 *
 * Implementations of this interface recursively adjusts an object's expulsion flags.
 * It also operates physical objects depending on the current and future effective expulsion
 * state of the object.
 */
interface IExpulsionAdjuster
{
    /**
     * @param emigrate whether the call is caused by emigration. If true, the migration code
     * will take care of disposal of file contents.
     * @param soid the id of the object being moved
     * @param pOld the old path of the object before movement. if the adjustment is not caused
     * by movement, it should be identical to the new path.
     * @param flags the new flags to be set on the object identified by {@code soid}.
     */
    void adjust_(boolean emigrate, PhysicalOp op, SOID soid, Path pOld, int flags, Trans t)
            throws IOException, ExNotFound, SQLException, ExNotDir, ExStreamInvalid, ExAlreadyExist;
}
