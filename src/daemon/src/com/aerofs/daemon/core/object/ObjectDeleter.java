package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;

public class ObjectDeleter
{
    private final ObjectMover _om;

    @Inject
    public ObjectDeleter(ObjectMover om)
    {
        _om = om;
    }

    public void delete_(SOID soid, PhysicalOp op, Trans t)
            throws Exception
    {
        String name = soid.oid().toStringFormal();
        _om.moveInSameStore_(soid, OID.TRASH, name, op, true, t);
    }

    /**
     * @param sidEmigrantTarget the SID of the store to which the object has been
     * emigrated. Note that when "emigrating" a folder, the parameter must
     * be set to the target SID as well, although strictly speaking folders don't
     * support migration. this is to enable Migration.detectAndPerformEmigration_()
     * to notice the emigration of the children when downloading the folder.
     * See comment (A) in that method.
     */
    public void deleteAndEmigrate_(SOID soid, PhysicalOp op, @Nonnull SID sidEmigrantTarget,
            Trans t)
            throws Exception
    {
        String name = EmigrantUtil.getDeletedObjectName_(soid, sidEmigrantTarget);
        _om.moveInSameStore_(soid, OID.TRASH, name, op, true, t);
    }
}
