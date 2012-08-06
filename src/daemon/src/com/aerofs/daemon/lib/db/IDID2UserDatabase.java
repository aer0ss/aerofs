/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.DID;

import javax.annotation.Nullable;
import java.sql.SQLException;

/**
 * When possible, use the DID2User class which provides a high-level wrapper around this low-level
 * class.
 */
public interface IDID2UserDatabase
{
    /**
     * Adds a DID-to-user mapping
     *
     * @pre the mapping must not already exist
     */
    void add_(DID did, String user, Trans t) throws SQLException;

    /**
     * @return the user id mapped to the given DID. null if the mapping doesn't exist
     */
    @Nullable String getNullable_(DID did) throws SQLException;
}
