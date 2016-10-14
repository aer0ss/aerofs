package com.aerofs.daemon.core.phy.linked.fid;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

/**
 * This interface maintains the value of the FID field in the metadata database. Each physical
 * object owns a unique instance of the interface, created through IFIDMaintainer.Factory.
 */
public interface IFIDMaintainer
{
    public static class InconsistentFIDException extends IOException
    {
        private static final long serialVersionUID = 0L;

        InconsistentFIDException(String message)
        {
            super(message);
        }
    }

    /**
     * On case-insensitive filesystems there are numerous opportunities for race conditions
     * to break typical consistency expectations. When applying changes from logical filesystem
     * to physical filesystem it is necessary to to check that the file on which the change
     * is about to be applied has not changed.
     *
     * For instance, if a user creates two files 'foo' and 'FOO' only one will be visible
     * on a case-insensitive system, say 'foo'. Now if the user delete that visible file 'foo'
     * and immediately create a new file 'FOO' the existing 'FOO' will be renamed to 'FOO (2)'.
     *
     * So far so good. Now imagine the client receives an update form the network asking to
     * delete the original 'foo' while it is still in the TimeoutDeletionBuffer.
     *
     * If FID consistency is not checked before acting upon the deletion this will have the
     * unintended result of deleting 'FOO'. Similar issues can arise with moves.
     *
     * Throwing will prevent the remote logical operation from being applied until the core
     * DB catches up with local physical changes.
     *
     * @throws InconsistentFIDException if DB and FS FID do not match
     */
    void throwIfFIDInconsistent_() throws IOException, SQLException;

    /**
     * Called when the physical object has been created.
     */
    void physicalObjectCreated_(Trans t) throws IOException, SQLException;

    /**
     * Called when the physical object has been moved.
     * @param fidm the maintainer instance owned by the destination physical object
     */
    void physicalObjectMoved_(IFIDMaintainer fidm, Trans t) throws IOException, SQLException;

    /**
     * Called when the physical object has been deleted.
     *
     * The method should operate the same regardless of whether it's a mapping or remapping.
     * Therefore we don't need a remap parameter.
     */
    void physicalObjectDeleted_(Trans t) throws SQLException;

    /**
     * The method should operate the same regardless of whether it's a mapping or remapping.
     * Therefore we don't need a remap parameter.
     */
    public static class Factory
    {
        private final DirectoryService _ds;
        private final InjectableDriver _dr;

        @Inject
        public Factory(InjectableDriver dr, DirectoryService ds)
        {
            _dr = dr;
            _ds = ds;
        }

        /**
         * This method is equivalent to {@code create_(new SOKID(soid, KIndex.MASTER), f)}.
         */
        public IFIDMaintainer create_(SOID soid, InjectableFile f)
        {
            return new MasterFIDMaintainer(_ds, _dr, f, soid);
        }

        /**
         * @param sokid the SOKID of the physical object that will own the created maintainer
         * @param f the PhysicalFile of the physical object that will own the created maintainer
         * @return
         */
        public IFIDMaintainer create_(SOKID sokid, InjectableFile f)
        {
            // Because the FID is relevant only to the master branch, passing a null maintainer for
            // non-master branches.
            if (sokid.kidx().equals(KIndex.MASTER)) return create_(sokid.soid(), f);
            else return new NonMasterFIDMaintainer();
        }
    }
}
