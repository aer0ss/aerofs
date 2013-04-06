package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

/**
 * This interface is the main access point of the physical storage layer. See
 * docs/design/physical_storage_class_diagram.pptx for a high-level class diagram.
 */
public interface IPhysicalStorage
{
    IPhysicalFile newFile_(SOKID sokid, Path path);

    IPhysicalFolder newFolder_(SOID soid, Path path);

    IPhysicalPrefix newPrefix_(SOCKID k) throws SQLException;

    /**
     * Move the completely downloaded prefix in place of the file.
     *
     * N.B. should rollback the operation if the transaction is aborted
     *
     * @return the amended mtime for the new file. for LocalFile's, Some file systems don't support
     * high-res timestamp as specified in mtimeNew. the return value reflects the actual mtime after
     * the prefix is applied to the filesystem.
     */
    long apply_(IPhysicalPrefix prefix, IPhysicalFile file, boolean wasPresent, long mtime, Trans t)
            throws IOException, SQLException;

    IPhysicalRevProvider getRevProvider();

    void init_() throws IOException;

    /**
     * Perform necessary operations on the physical storage for creating a store.
     *
     * NB: No assumptions should be made about pathes. Any code that require path information
     * should take place in the folder to anchor promotion method
     */
    void createStore_(SIndex sidx, SID sid, Trans t) throws IOException, SQLException;

    /**
     * Perform necessary operations on the physical storage for deleting a store. The implementation
     * should at least delete prefix files belonging to the store
     *
     * NB: No assumptions should be made about pathes. All physical objects in the store are deleted
     * prior to this method being called
     */
    void deleteStore_(SIndex sidx, SID sid, PhysicalOp op, Trans t) throws IOException, SQLException;
}
