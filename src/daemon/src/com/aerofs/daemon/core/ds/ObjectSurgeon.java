/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;

import java.sql.SQLException;

/**
 * A "surgical" subset of DirectoryService operation only used by Aliasing
 */
public interface ObjectSurgeon
{
    /**
     * Replace an aliased OID with its target
     *
     * This method will:
     * 1. update the parent of all children of the {@code alias} to {@code target}
     * 2. replace the oid of the {@code alias} entry in the OA table with {@code target}
     * 3. update the oid of all CAs of the {@code alias} to {@code target}
     *
     * @pre there is no entry for {@code target} in the OA table
     *
     * USE WITH EXTREME CAUTION (Aliasing is the only expected caller)
     */
    void replaceOID_(SOID alias, SOID target, Trans t) throws SQLException, ExAlreadyExist;

    void swapOIDsInSameStore_(SIndex sidx, OID oid1, OID oid2, Trans t)
            throws SQLException, ExNotFound, ExNotDir;

    /**
     * Deleting meta-data entry is currently only required while performing aliasing.
     */
    void deleteOA_(SOID soid, Trans t) throws SQLException;
}
