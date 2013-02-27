package com.aerofs.daemon.core.phy.linked.fid;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.IOException;
import java.sql.SQLException;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

/**
 * This class maintains the FID values for master branches of a file.
 */
public class MasterFIDMaintainer implements IFIDMaintainer
{
    private static final Logger l = Loggers.getLogger(MasterFIDMaintainer.class);

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
        if (soidOld != null && !_soid.equals(soidOld)) _ds.unsetFID_(soidOld, t);

        _ds.setFID_(_soid, fid, t);
    }

    @Override
    public void physicalObjectMoved_(IFIDMaintainer to, Trans t) throws IOException, SQLException
    {
        if (to instanceof NonMasterFIDMaintainer) {
            // reset the FID of the source object
            _ds.unsetFID_(_soid, t);

        } else {
            assert to instanceof MasterFIDMaintainer;
            MasterFIDMaintainer mfmTo = (MasterFIDMaintainer) to;

            if (_soid.equals(mfmTo._soid)) return;

            FID fid;
            try {
                fid = getFIDFromFilesystem_(mfmTo._f);
            } catch (ExFileNotFound e) {
                if (_soid.oid().equals(mfmTo._soid.oid())) {
                    // we cannot afford to cause a migration to fail simply because of a missing
                    // file (lest we end up with a crash loop in the scanner causing a no-launch)
                    // so we simply transfer the fid from the source to the target
                    fid = _ds.getOA_(_soid).fid();
                    // should not try to move a file for which no master CA exists...
                    assert fid != null;
                } else {
                    throw e;
                }
            }

            // Fallible assertion (see above). It asserts that the FID is unchanged from the source
            // logical object to the target physical object.
            falliblyAssert(fid.equals(_ds.getOA_(_soid).fid()));
            // reset the FID of the source object
            _ds.unsetFID_(_soid, t);
            // set the FID of the destination object
            _ds.setFID_(mfmTo._soid, fid, t);
        }
    }

    @Override
    public void physicalObjectDeleted_(Trans t) throws SQLException
    {
        // the object's FID may be already null if the FID has been assigned to another object by
        // physicalObjectCreated_ or physicalObjectMoved_
        _ds.unsetFID_(_soid, t);
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
