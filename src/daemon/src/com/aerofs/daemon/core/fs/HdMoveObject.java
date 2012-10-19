package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.fs.EIMoveObject;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Role;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdMoveObject extends AbstractHdIMC<EIMoveObject>
{
    private final DirectoryService _ds;
    private final LocalACL _lacl;
    private final TransManager _tm;
    private final ObjectMover _om;
    private final ImmigrantCreator _imc;

    @Inject
    public HdMoveObject(ObjectMover om, TransManager tm, LocalACL lacl, DirectoryService ds,
            ImmigrantCreator imc)
    {
        _om = om;
        _tm = tm;
        _lacl = lacl;
        _ds = ds;
        _imc = imc;
    }

    @Override
    protected void handleThrows_(EIMoveObject ev, Prio prio) throws Exception
    {
        SOID soid = _ds.resolveThrows_(ev._from);

        if (soid.oid().isTrash() || soid.oid().isRoot()) {
            throw new ExNoPerm("can't move system folders");
        }

        // TODO check REMOVE_CHILD right on the parent, if the parents are different
        _lacl.checkThrows_(ev.user(), soid.sidx(), Role.EDITOR);
        SOID soidToParent = _lacl.checkThrows_(ev.user(), ev._toParent, Role.EDITOR);

        Trans t = _tm.begin_();
        try {
            move_(soid, soidToParent, ev._toName, PhysicalOp.APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }


    /**
     * This method either moves objects within the same store, or across stores via migration,
     * depending on whether the old sidx is the same as the new one.
     *
     * @return the SOID of the object after the move. This new SOID may be different from
     * the parameter {@code soid} if migration occurs.
     *
     * Note: This is a method operate at the top most level, while ObjectMover and ImmigrantCreator
     * operate at lower levels. That's why we didn't put the method to ObjectMover. Also because
     * ObjectMover operates at a level even lower than ImmigrantCreator, having the method in
     * ObjectMover would require this class to refer to ImmigrantCreator, which is inappropriate.
     */
    public SOID move_(SOID soid, SOID soidToParent, String toName, PhysicalOp op, Trans t)
            throws Exception
    {
        if (soidToParent.sidx().equals(soid.sidx())) {
            _om.moveInSameStore_(soid, soidToParent.oid(), toName, op, false, true, t);
            return soid;
        } else {
            return _imc.createImmigrantRecursively_(soid, soidToParent, toName, op, t);
        }
    }
}
