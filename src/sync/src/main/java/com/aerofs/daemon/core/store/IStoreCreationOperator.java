/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

public interface IStoreCreationOperator
{
    void createStore_(SIndex sidx, Trans t) throws SQLException;
}
