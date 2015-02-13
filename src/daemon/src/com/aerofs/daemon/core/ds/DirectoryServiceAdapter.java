/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.ids.OID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import java.sql.SQLException;

public class DirectoryServiceAdapter implements IDirectoryServiceListener
{
    @Override public void objectCreated_(SOID obj, OID parent, Path pathTo, Trans t)
            throws SQLException {}
    @Override public void objectDeleted_(SOID obj, OID parent, Path pathFrom, Trans t)
            throws SQLException {}
    @Override public void objectMoved_(SOID obj, OID parentFrom, OID parentTo,
            Path pathFrom, Path pathTo, Trans t) throws SQLException {}
    @Override public void objectContentCreated_(SOKID obj, Path path, Trans t)
            throws SQLException {}
    @Override public void objectContentDeleted_(SOKID obj, Trans t)
            throws SQLException {}
    @Override public void objectContentModified_(SOKID obj, Path path, Trans t)
            throws SQLException {}
    @Override public void objectObliterated_(OA oa, Trans t) throws SQLException {}
}
