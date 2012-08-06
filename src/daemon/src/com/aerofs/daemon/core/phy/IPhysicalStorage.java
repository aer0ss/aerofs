package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

public interface IPhysicalStorage
{
    IPhysicalFile newFile_(SOKID sokid, Path path);

    IPhysicalFolder newFolder_(SOID soid, Path path);

    IPhysicalPrefix newPrefix_(SOCKID k);

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
     * @param path During store creation, the system mat not be in a consistent state and therefore
     * we pass the path as a parameter rather than letting the implementation query
     * DirectoryService. The path is empty for root stores.
     */
    void createStore_(SIndex sidx, Path path, Trans t) throws IOException, SQLException;

    /**
     * Perform necessary operations on the physical storage for deleting a store. The implementation
     * should at least delete prefix files belonging to the store
     * @param path During store deletion, the system mat not be in a consistent state and therefore
     * we pass the path as a parameter rather than letting the implementation to query
     * DirectoryService.
     */
    void deleteStore_(SIndex sidx, Path path, PhysicalOp op, Trans t) throws IOException, SQLException;
}
