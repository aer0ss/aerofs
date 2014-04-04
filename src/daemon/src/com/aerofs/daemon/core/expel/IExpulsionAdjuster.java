package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;

/**
 * After moving an object or changing its expulsion flag, physical objects also need to be moved,
 * deleted, or recreated depending on the situation.
 */
interface IExpulsionAdjuster
{
    /**
     * Called *after* an object is moved or its expulsion state changes
     *
     * @param pathOld the old path of the object before movement. if the adjustment is not caused
     * by movement, it should be identical to the new path.
     * @param soid the id of the object being moved
     * @param emigrate whether the call is caused by emigration. If true, the migration code
     * will take care of disposal of file contents.
     */
    void adjust_(ResolvedPath pathOld, SOID soid, boolean emigrate, PhysicalOp op, Trans t)
            throws Exception;
}
