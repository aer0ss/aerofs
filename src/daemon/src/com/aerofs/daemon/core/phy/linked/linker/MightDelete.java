package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.sql.SQLException;

public class MightDelete
{
    private final IgnoreList _il;
    private final DirectoryService _ds;
    private final RepresentabilityHelper _rh;

    @Inject
    public MightDelete(IgnoreList il, DirectoryService ds, RepresentabilityHelper rh)
    {
        _il = il;
        _ds = ds;
        _rh = rh;
    }

    /**
     * N.B. According to {@link IDeletionBuffer#add_(SOID)}, IDeletionBuffer.remove_() must be
     * called on exceptions. But the caller doesn't know the soid to remove after this method
     * returns. Therefore, callers must not throw exceptions after this method.
     */
    void mightDelete_(PathCombo pcPhysical, IDeletionBuffer delBuffer) throws SQLException
    {
        if (_il.isIgnored(pcPhysical._path.last())) return;

        // TODO acl checking

        // ignore if no logical object is found
        SOID soid = _ds.resolveNullable_(pcPhysical._path);
        if (soid == null) return;

        OA oa = _ds.getOA_(soid);
        // must not add object to the deletion buffer if it is not expected to be present
        if (shouldNotDelete(oa) || _rh.isNonRepresentable(oa)) return;

        delBuffer.add_(soid);

        // Do not do anything that may throw after delBuffer.add_() above. Or you have to undo the
        // operation by calling remove_() on exceptions. See comments in add_().
    }

    /**
     * @return true if the object should not be added to the deletion buffer in any case
     */
    public static boolean shouldNotDelete(@Nonnull OA oa)
    {
        if (oa.isExpelled()) return true;

        // don't delete files whose master branches are still being downloaded.
        if (oa.isFile() && oa.caMasterNullable() == null) return true;

        return false;
    }
}
