package com.aerofs.daemon.core.phy.linked.fid;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.IOException;
import java.sql.SQLException;

import javax.annotation.Nonnull;

import org.apache.log4j.Logger;

public class FIDMaintainer implements IFIDMaintainer
{
    private static final Logger l = Util.l(FIDMaintainer.class);

    private final DirectoryService _ds;
    private final InjectableDriver _dr;

    private final SOID _soid;
    private final InjectableFile _f;

    FIDMaintainer(DirectoryService ds, InjectableDriver dr, SOID soid, InjectableFile f)
    {
        _dr = dr;
        _ds = ds;
        _soid = soid;
        _f = f;
    }

    /**
     * Fallible assertions: these are the assertions that should hold in most cases.
     * But because of concurrent activities in the local filesystem, they may fail in rare cases.
     * We ignore failed assertions and move on, and the linker will pick up changes caused
     * by concurrent activities later on.
     *
     * N.B. the expression is not optimized off even if assertion is disabled. So avoid expensive
     * expressions.
     */
    private static void falliblyAssert(boolean truth)
    {
        if (!truth) l.warn("fallable assertion error: ", new Exception());
    }

    @Override
    public void physicalObjectCreated_(Trans t) throws IOException, SQLException
    {
        FID fid = getFID_();

        SOID soidOld = _ds.getSOID_(fid);
        // The FID to be remapped must already exist in the db.
        if (soidOld != null && !_soid.equals(soidOld)) _ds.setFID_(soidOld, null, t);

        _ds.setFID_(_soid, fid, t);
    }

    @Override
    public void physicalObjectMoved_(IFIDMaintainer to, Trans t) throws IOException, SQLException
    {
        // We don't support moving to a physical object with a NullFIDMaintainer right now.
        FIDMaintainer fidmTo = (FIDMaintainer) to;

        if (!_soid.equals(fidmTo._soid)) {
            FID fid = fidmTo.getFID_();
            // Fallible assertion (see above). It asserts that the FID is unchanged from the source
            // object to the target object.
            falliblyAssert(fid.equals(_ds.getOANullable_(_soid).fid()));
            _ds.setFID_(_soid, null, t);
            _ds.setFID_(fidmTo._soid, fid, t);
        }
    }

    @Override
    public void physicalObjectDeleted_(Trans t) throws SQLException
    {
        // the object's FID may be already null if the FID has been assigned to another object by
        // physicalObjectCreated_ or physicalObjectMoved_
        _ds.setFID_(_soid, null, t);
    }

    private @Nonnull FID getFID_() throws IOException, SQLException
    {
        FID fid = _dr.getFID(_f.getPath());
        if (fid == null) throw new IOException("OS-specific file");
        return fid;
    }
}
