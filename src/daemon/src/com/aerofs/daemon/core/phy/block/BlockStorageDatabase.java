/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.sql.SQLException;

/**
 * Maintains mapping between logical object (SOKID) and physical objects (64bit index)
 *
 * For each physical object, keep track of:
 *      version (64bit integer), to distinguish revisions
 *      length
 *      mtime
 *      content (ordered list of blocks)
 *
 * For each block, keep track of:
 *      length
 *      content hash
 *      reference count
 *      state (to handle remote storage backends)
 *
 * In addition to the "live" physical objects, this databse also tracks "dead" ones to provide
 * revision history. Things get a little more involved as the IPhysicalRevProvider interface
 * uses Path instead of logical objects. We therefore have to maintain an alternate object tree
 * to track revisions.
 */
public class BlockStorageDatabase extends AbstractDatabase
{
    private static final Logger l = Util.l(BlockStorageDatabase.class);

    public static final long FILE_ID_NOT_FOUND = -1;

    public static final long DIR_ID_NOT_FOUND = -1;
    public static final long DIR_ID_ROOT = -2;

    public static final long DELETED_FILE_LEN = -1;
    public static final long DELETED_FILE_DATE = 0;
    public static final ContentHash DELETED_FILE_CHUNKS = new ContentHash(new byte[0]);

    public static final ContentHash EMPTY_FILE_CHUNKS = new ContentHash(new byte[0]);

    @Inject
    public BlockStorageDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    public void init_() throws SQLException
    {
    }
}
