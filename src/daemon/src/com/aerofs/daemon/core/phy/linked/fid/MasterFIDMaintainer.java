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

/**
 * This class maintains the FID values for master branches of a file.
 */
public class MasterFIDMaintainer implements IFIDMaintainer
{
    private static final Logger l = Util.l(MasterFIDMaintainer.class);

    private final DirectoryService _ds;
    private final InjectableDriver _dr;

    private final SOID _soid;
    private final InjectableFile _f;

    MasterFIDMaintainer(DirectoryService ds, InjectableDriver dr, InjectableFile f, SOID soid)
    {
        assert soid != null;
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
        FID fid = getFIDFromFilesystem_(_f);

        SOID soidOld = _ds.getSOIDNullable_(fid);

        // unmap the FID first if it exist for other objects
        if (soidOld != null && !_soid.equals(soidOld)) _ds.setFID_(soidOld, null, t);

        _ds.setFID_(_soid, fid, t);
    }

    @Override
    public void physicalObjectMoved_(IFIDMaintainer to, Trans t) throws IOException, SQLException
    {
        if (to instanceof NonMasterFIDMaintainer) {
            // reset the FID of the source object
            _ds.setFID_(_soid, null, t);

        } else {
            assert to instanceof MasterFIDMaintainer;
            MasterFIDMaintainer mfmTo = (MasterFIDMaintainer) to;

            if (_soid.equals(mfmTo._soid)) return;

            FID fid = getFIDFromFilesystem_(mfmTo._f);
            // Fallible assertion (see above). It asserts that the FID is unchanged from the source
            // logical object to the target physical object.
            falliblyAssert(fid.equals(_ds.getOA_(_soid).fid()));
            // reset the FID of the source object
            _ds.setFID_(_soid, null, t);
            // set the FID of the destination object
            _ds.setFID_(mfmTo._soid, fid, t);
        }
    }

    @Override
    public void physicalObjectDeleted_(Trans t) throws SQLException
    {
        // the object's FID may be already null if the FID has been assigned to another object by
        // physicalObjectCreated_ or physicalObjectMoved_
        _ds.setFID_(_soid, null, t);
    }

    /**
     * Read the object's FID from the filesystem and write it to the database.
     */
    void setFIDFromFilesystem_(Trans t) throws SQLException, IOException
    {
        _ds.setFID_(_soid, getFIDFromFilesystem_(_f), t);
    }

    private @Nonnull FID getFIDFromFilesystem_(InjectableFile f) throws IOException
    {
        FID fid = _dr.getFID(f.getAbsolutePath());
        if (fid == null) throw new IOException("OS-specific file");
        return fid;
    }
}
