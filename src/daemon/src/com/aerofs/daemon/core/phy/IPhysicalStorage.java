package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.ImmutableCollection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This interface is the main access point of the physical storage layer. See
 * docs/design/physical_storage_class_diagram.pptx for a high-level class diagram.
 */
public interface IPhysicalStorage extends IStartable
{
    IPhysicalFile newFile_(ResolvedPath path, KIndex kidx) throws SQLException;

    IPhysicalFolder newFolder_(ResolvedPath path) throws SQLException;

    IPhysicalPrefix newPrefix_(SOCKID k, @Nullable String scope) throws SQLException;

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

    void init_() throws IOException, SQLException;

    /**
     * Perform necessary operations on the physical storage for creating a store.
     *
     * NB: No assumptions should be made about pathes. Any code that require path information
     * should take place in the folder to anchor promotion method
     */
    void createStore_(SIndex sidx, SID sid, String name, Trans t) throws IOException, SQLException;

    /**
     * Perform necessary operations on the physical storage for deleting a store. The implementation
     * should at least delete prefix files belonging to the store
     *
     * NB: This is called *after* store cleanup
     *    - All objects have been deleted/scrubbed
     *    - The SIndex is *not* admitted (hence not part of the store hierarchy)
     */
    void deleteStore_(SID physicalRoot, SIndex sidx, SID sid, Trans t) throws IOException, SQLException;

    /**
     * Delete prefix associated with a given SOKID, if any
     */
    void deletePrefix_(SOKID sokid) throws IOException, SQLException;

    /**
     * Override CfgStoragePolicy for the given transaction
     *
     * After calling this method, all files deleted within the given transaction will be effectively
     * deleted (instead of simply being moved to rev) when the transaction is successfully committed
     */
    void discardRevForTrans_(Trans t);

    /**
     * @return if true, files deleted within the given transaction are not kept in sync history
     */
    boolean isDiscardingRevForTrans_(Trans t);

    public static class NonRepresentableObject
    {
        public final SOID soid;
        public final @Nullable OID conflict;

        public NonRepresentableObject(SOID soid, @Nullable OID conflict)
        {
            this.soid = soid;
            this.conflict = conflict;
        }
    }

    /**
     * List NROs, if the underlying storage has limitations (only applies to LinkedStorage at
     * this time and in the foreseeable future)
     */
    ImmutableCollection<NonRepresentableObject> listNonRepresentableObjects_()
            throws IOException, SQLException;

    /**
     * For incremental expulsion, storage backends that can be accessed without going
     * through the daemon (i.e. LINKED storage) MUST offer a way to recursively delete
     * folders in a way that *appears* atomic to the outside world.
     */
    void deleteFolderRecursively_(ResolvedPath path, PhysicalOp op, Trans t)
            throws SQLException, IOException;

    /**
     * External roots need not be scrubbed as the aux root is discarded
     */
    boolean shouldScrub_(SID sid);

    /**
     * For storage backends that do not duplicate path information for all objects,
     * {@link #deleteFolderRecursively_} is not sufficient to perform a thorough cleanup,
     * hence the existennce of this method.
     *
     * Specifically:
     *   - BlockStorage maintains NO path->object mapping
     *   - LinkedStorage does not maintain path->object mappping for conflicts and NROs
     *
     * @param historyPath path under which to preserve old versions. If empty, files are discarded
     */
    void scrub_(SOID soid, @Nonnull Path historyPath, Trans t)
            throws SQLException, IOException;
}
