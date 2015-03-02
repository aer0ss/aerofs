/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

/**
 * Implementations of this interface execute necessary operations when deleting a store. As many
 * subsystems are involved in store deletion, using a mediator, StoreDeletionOperators, to connect
 * StoreDeleter and these operators reverses their dependency.
 */
public interface IStoreDeletionOperator
{
    void deleteStore_(SIndex sidx, Trans t) throws SQLException;
}
