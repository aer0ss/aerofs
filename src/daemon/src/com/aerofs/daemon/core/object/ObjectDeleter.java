package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.migration.EmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import javax.annotation.Nullable;

public class ObjectDeleter
{
    private ObjectMover _om;

    @Inject
    public void inject_(ObjectMover om)
    {
        _om = om;
    }

    /**
     * @param sidEmigrantTarget the SID of the store to which the object has been
     * emigrated. Null for non-emigrating deletion.
     * Note that when "emigrating" a folder, the parameter must
     * be set to the target SID as well, although strictly speaking folders don't
     * support migration. this is to enable Migration.detectAndPerformEmigration_()
     * to notice the emigration of the children when downloading the folder.
     * See comment (A) in that method.
     */
    public void delete_(SOID soid, PhysicalOp op, @Nullable SID sidEmigrantTarget,
            Trans t) throws Exception
    {
        String name = EmigrantCreator.getDeletedObjectName_(soid, sidEmigrantTarget);
        _om.moveInSameStore_(soid, OID.TRASH, name, op, sidEmigrantTarget != null, true, t);
    }
}
